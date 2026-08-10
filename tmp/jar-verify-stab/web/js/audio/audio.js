import { Logger } from "../utils/logger.js";
import { StreamingResampler } from "./resampler.js";
import {
    FRAME_SAMPLES,
    FRAME_BYTES,
    TARGET_SAMPLE_RATE,
    FrameAccumulator,
    floatToPcm16Sample
} from "./pcm.js";

export class SvgAudio {

    static MIC_HOLD_MS = 120;
    static PACKET_SIZE = FRAME_SAMPLES;
    static DIAG_EVERY_MS = 2000;
    static AUDIO_SETTINGS_KEY = "svg.audio.settings.v1";

    audioContext;
    audioWorkletNode;

    constructor() {
        this.micHandler = null;
        this.microphoneStream = null;
        this.micNode = null;
        this.micSource = null;
        this.micSilentGain = null;
        this.muted = false;
        this.micActiveUntil = 0;
        this.speechRing = new Uint8Array(FRAME_SAMPLES * 16);
        this.speechWrite = 0;
        this.speechRead = 0;
        this.speechAvailable = 0;
        this.micIndicator = null;
        this.levelMeterEl = null;
        this.isPttActive = () => true;
        this.getTransmitMode = () => "voice";

        /** @type {StreamingResampler|null} */
        this.resampler = null;
        /** @type {FrameAccumulator} */
        this.frameAccumulator = new FrameAccumulator(FRAME_SAMPLES);
        this.contextSampleRate = TARGET_SAMPLE_RATE;
        this.contextChannelCount = 1;
        this.micStartGeneration = 0;
        this.activeMicGeneration = 0;
        this.outgoingFrames = 0;
        this.outgoingBytes = 0;
        this.lastDiagAt = 0;
        this.lastLevel = 0;
        this.pipelineId = `audio-${Date.now().toString(36)}`;
        this.selectedDeviceId = "";
        this.micRebuildInFlight = false;
        this.micRebuildQueued = false;
        this.micRebuildQueuedDeviceId = undefined;
        this.processing = this.#loadProcessingPrefs();
        this.appliedTrackSettings = null;
        this.processingSupport = {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
        };
        this.vadBypass = false;
        this.diagSignalActive = false;
        this.diagSignalSamplesLeft = 0;
        this.diagSignalPhase = 0;
        this.onAppliedSettingsChange = null;

        this.audioRuntime = {
            audioContextSupported: false,
            workletSupported: false,
            mediaDevicesSupported: false,
            canCaptureMic: false,
            canSelectOutput: false,
            sampleRate: 0,
            degradedReason: ""
        };
    }

    getAudioContextCtor() {
        return window.AudioContext || window.webkitAudioContext || null;
    }

    resolveAudioModuleUrl(moduleName) {
        const url = new URL(moduleName, import.meta.url);
        // import.meta.url query is dropped by URL resolution — re-apply build cache bust.
        const buildId = (typeof window !== "undefined" && window.BUILD_ID) || url.searchParams.get("v");
        if (buildId && buildId !== "@@GIT_COMMIT@@") {
            url.searchParams.set("v", String(buildId));
        }
        return url.href;
    }

    async initAudio() {
        const AudioContextCtor = this.getAudioContextCtor();
        this.audioRuntime.audioContextSupported = !!AudioContextCtor;
        this.audioRuntime.mediaDevicesSupported = !!(navigator.mediaDevices
            && typeof navigator.mediaDevices.getUserMedia === "function"
            && typeof navigator.mediaDevices.enumerateDevices === "function");

        window.audioElement = document.createElement("audio");
        this.audioRuntime.canSelectOutput = !!window.audioElement?.setSinkId;

        if (!AudioContextCtor) {
            this.audioRuntime.degradedReason = "AudioContext is unavailable in this browser.";
            Logger.log(`[Audio] ${this.audioRuntime.degradedReason}`);
            return { ...this.audioRuntime };
        }

        // Request 48 kHz but inspect the actual rate — many browsers ignore the hint.
        this.audioContext = new AudioContextCtor({ sampleRate: TARGET_SAMPLE_RATE });
        this.contextSampleRate = this.audioContext.sampleRate || TARGET_SAMPLE_RATE;
        this.audioRuntime.sampleRate = this.contextSampleRate;

        if (this.contextSampleRate !== TARGET_SAMPLE_RATE) {
            Logger.log(
                `[Audio] AudioContext.sampleRate=${this.contextSampleRate}; ` +
                `streaming resample → ${TARGET_SAMPLE_RATE} Hz`
            );
        }

        this.#ensureResampler(this.contextSampleRate);

        this.audioRuntime.workletSupported = !!(this.audioContext.audioWorklet && typeof AudioWorkletNode !== "undefined");
        if (!this.audioRuntime.workletSupported) {
            this.audioRuntime.degradedReason = "AudioWorklet is unavailable, receive/mic processing is limited.";
            Logger.log(`[Audio] ${this.audioRuntime.degradedReason}`);
            this.audioRuntime.canCaptureMic = false;
            this.audioRuntime.canSelectOutput = this.audioRuntime.canSelectOutput || !!this.audioContext.setSinkId;
            return { ...this.audioRuntime };
        }

        try {
            await this.audioContext.audioWorklet.addModule(this.resolveAudioModuleUrl("speaker.js"));
            await this.audioContext.audioWorklet.addModule(this.resolveAudioModuleUrl("microphone.js"));

            this.audioWorkletNode = new AudioWorkletNode(this.audioContext, "pcm-player", {
                numberOfInputs: 0,
                numberOfOutputs: 1,
                outputChannelCount: [2]
            });
            this.audioWorkletNode.connect(this.audioContext.destination);
        } catch (error) {
            this.audioRuntime.degradedReason = "Failed loading audio worklets.";
            Logger.log(`[Audio] ${this.audioRuntime.degradedReason}`);
            console.error(error);
            this.audioWorkletNode = null;
        }

        this.audioRuntime.canCaptureMic = !!this.audioWorkletNode && this.audioRuntime.mediaDevicesSupported;
        this.audioRuntime.canSelectOutput = this.audioRuntime.canSelectOutput || !!this.audioContext.setSinkId;

        if (!this.audioRuntime.canCaptureMic && !this.audioRuntime.degradedReason) {
            this.audioRuntime.degradedReason = "Microphone capture is unavailable in this browser/context.";
        }

        return { ...this.audioRuntime };
    }

    /**
     * @param {number} inputRate
     */
    #ensureResampler(inputRate) {
        const rate = Number.isFinite(inputRate) && inputRate > 0 ? inputRate : TARGET_SAMPLE_RATE;
        if (!this.resampler || this.resampler.inputRate !== rate || this.resampler.outputRate !== TARGET_SAMPLE_RATE) {
            this.resampler = new StreamingResampler(rate, TARGET_SAMPLE_RATE);
            Logger.debug(
                `[Audio] resampler pipeline=${this.pipelineId} ratio=${(rate / TARGET_SAMPLE_RATE).toFixed(6)} ` +
                `in=${rate} out=${TARGET_SAMPLE_RATE}`
            );
        }
    }

    setMicIndicator(el) {
        this.micIndicator = el;
    }

    setLevelMeter(el) {
        this.levelMeterEl = el;
    }

    onMicData(handler) {
        this.micHandler = handler;
    }

    setTransmitModeProvider(fn) {
        this.getTransmitMode = fn;
    }

    setPttActiveProvider(fn) {
        this.isPttActive = fn;
    }

    /**
     * @returns {number} active microphone graph generation (0 when stopped)
     */
    getActiveMicGeneration() {
        return this.activeMicGeneration;
    }

    /**
     * @returns {boolean}
     */
    isMicRunning() {
        return !!(this.micNode && this.microphoneStream);
    }

    async startMic(deviceId) {
        if (!this.audioRuntime.canCaptureMic || !this.audioContext) {
            throw new Error("Microphone capture is not supported in this browser/context.");
        }

        const generation = ++this.micStartGeneration;
        if (this.isMicRunning()) {
            Logger.debug(`[Audio] duplicate startMicrophone() ignored until prior graph disposed gen=${generation}`);
        }

        // Full cleanup before starting a new capture graph (reconnect safety).
        this.stopMic();

        if (generation !== this.micStartGeneration) {
            return;
        }

        await this.audioContext.resume();
        this.contextSampleRate = this.audioContext.sampleRate || TARGET_SAMPLE_RATE;
        this.audioRuntime.sampleRate = this.contextSampleRate;
        this.#ensureResampler(this.contextSampleRate);
        this.resampler.reset();
        this.frameAccumulator.reset();
        this.outgoingFrames = 0;
        this.outgoingBytes = 0;
        this.pipelineId = `audio-${generation}-${Date.now().toString(36)}`;
        if (deviceId != null && deviceId !== "") {
            this.selectedDeviceId = String(deviceId);
        }

        const audioConstraints = {
            echoCancellation: { ideal: !!this.processing.echoCancellation },
            noiseSuppression: { ideal: !!this.processing.noiseSuppression },
            autoGainControl: { ideal: !!this.processing.autoGainControl },
            channelCount: { ideal: 1 },
            sampleRate: { ideal: TARGET_SAMPLE_RATE }
        };
        if (this.selectedDeviceId) {
            audioConstraints.deviceId = { exact: this.selectedDeviceId };
        }

        try {
            this.microphoneStream = await navigator.mediaDevices.getUserMedia({
                audio: audioConstraints
            });
        } catch (err) {
            // Soft-fallback: drop exact deviceId / processing ideals if overconstrained.
            if (err && (err.name === "OverconstrainedError" || err.name === "ConstraintNotSatisfiedError")) {
                Logger.debug(`[Audio] getUserMedia overconstrained; retrying with relaxed constraints`);
                const relaxed = {
                    channelCount: { ideal: 1 },
                    sampleRate: { ideal: TARGET_SAMPLE_RATE }
                };
                if (this.selectedDeviceId) {
                    try {
                        this.microphoneStream = await navigator.mediaDevices.getUserMedia({
                            audio: { ...relaxed, deviceId: { exact: this.selectedDeviceId } }
                        });
                    } catch {
                        this.selectedDeviceId = "";
                        this.microphoneStream = await navigator.mediaDevices.getUserMedia({ audio: relaxed });
                    }
                } else {
                    this.microphoneStream = await navigator.mediaDevices.getUserMedia({ audio: relaxed });
                }
            } else {
                throw err;
            }
        }

        if (generation !== this.micStartGeneration) {
            this.microphoneStream.getTracks().forEach((t) => t.stop());
            this.microphoneStream = null;
            return;
        }

        const track = this.microphoneStream.getAudioTracks()[0];
        const settings = track?.getSettings?.() || {};
        const capabilities = track?.getCapabilities?.() || {};
        this.contextChannelCount = settings.channelCount || 1;
        this.appliedTrackSettings = {
            sampleRate: settings.sampleRate || null,
            channelCount: settings.channelCount || null,
            deviceId: settings.deviceId ? "(set)" : null,
            echoCancellation: settings.echoCancellation,
            noiseSuppression: settings.noiseSuppression,
            autoGainControl: settings.autoGainControl
        };
        this.processingSupport = {
            echoCancellation: !Object.prototype.hasOwnProperty.call(capabilities, "echoCancellation")
                || (Array.isArray(capabilities.echoCancellation) ? capabilities.echoCancellation.length > 0 : true),
            noiseSuppression: !Object.prototype.hasOwnProperty.call(capabilities, "noiseSuppression")
                || (Array.isArray(capabilities.noiseSuppression) ? capabilities.noiseSuppression.length > 0 : true),
            autoGainControl: !Object.prototype.hasOwnProperty.call(capabilities, "autoGainControl")
                || (Array.isArray(capabilities.autoGainControl) ? capabilities.autoGainControl.length > 0 : true)
        };
        this.onAppliedSettingsChange?.(this.getAppliedMicStatus());
        Logger.debug(
            `[Audio] Mic started pipeline=${this.pipelineId} contextRate=${this.contextSampleRate} ` +
            `trackRate=${settings.sampleRate || "n/a"} channels=${this.contextChannelCount} ` +
            `requested echo=${!!this.processing.echoCancellation} ns=${!!this.processing.noiseSuppression} ` +
            `agc=${!!this.processing.autoGainControl} ` +
            `applied echo=${settings.echoCancellation} ns=${settings.noiseSuppression} ` +
            `agc=${settings.autoGainControl} activePipelines=1`
        );

        this.micSource = this.audioContext.createMediaStreamSource(this.microphoneStream);
        this.micNode = new AudioWorkletNode(this.audioContext, "mic-capture");

        // Keep the capture node in a live graph. Some engines pause worklets that
        // only have an upstream connection and no path toward the destination.
        this.micSilentGain = this.audioContext.createGain();
        this.micSilentGain.gain.value = 0;
        this.micSource.connect(this.micNode);
        this.micNode.connect(this.micSilentGain);
        this.micSilentGain.connect(this.audioContext.destination);

        this.activeMicGeneration = generation;
        this.micNode.port.onmessage = (event) => this.#handleMicMessage(event, generation);
        this.#syncVadBypassToWorklet();
    }

    /**
     * @returns {{
     *   requested: { echoCancellation: boolean, noiseSuppression: boolean, autoGainControl: boolean },
     *   applied: object|null,
     *   support: { echoCancellation: boolean, noiseSuppression: boolean, autoGainControl: boolean }
     * }}
     */
    getAppliedMicStatus() {
        return {
            requested: { ...this.processing },
            applied: this.appliedTrackSettings ? { ...this.appliedTrackSettings } : null,
            support: { ...this.processingSupport }
        };
    }

    /**
     * DEBUG-only: bypass SVG VAD and transmit continuous framed audio.
     * @param {boolean} enabled
     */
    setVadBypass(enabled) {
        this.vadBypass = !!enabled;
        this.#syncVadBypassToWorklet();
        Logger.debug(`[Audio] VAD bypass=${this.vadBypass}`);
    }

    #syncVadBypassToWorklet() {
        try {
            this.micNode?.port?.postMessage({ type: "setVadBypass", enabled: this.vadBypass });
        } catch {
            // ignore
        }
    }

    /**
     * DEBUG-only: inject a 1 kHz sine for `durationSec` through the normal PCM path.
     * Disabled unless localStorage svg.debug.tone=1 (never packaged as default-on).
     * @param {number} [durationSec=10]
     * @param {number} [hz=1000]
     * @param {number} [amplitude=0.2]
     */
    startDiagnosticTone(durationSec = 10, hz = 1000, amplitude = 0.2) {
        let allowed = false;
        try {
            allowed = localStorage.getItem("svg.debug.tone") === "1";
        } catch {
            allowed = false;
        }
        if (!allowed) {
            Logger.debug("[Audio] diagnostic tone refused (set localStorage svg.debug.tone=1)");
            return false;
        }
        const seconds = Math.min(30, Math.max(1, Number(durationSec) || 10));
        this.diagSignalPhase = 0;
        this.diagSignalSamplesLeft = Math.floor(TARGET_SAMPLE_RATE * seconds);
        this.diagSignalActive = true;
        this.diagSequence = 0;
        this.diagToneHz = hz;
        this.diagToneAmplitude = Math.min(0.8, Math.max(0.01, amplitude));
        Logger.debug(
            `[Audio] diagnostic tone start hz=${hz} amp=${this.diagToneAmplitude} duration=${seconds}s`
        );
        this.#pumpDiagnosticTone();
        return true;
    }

    #pumpDiagnosticTone() {
        if (!this.diagSignalActive || this.diagSignalSamplesLeft <= 0) {
            this.diagSignalActive = false;
            Logger.debug("[Audio] diagnostic tone complete");
            return;
        }

        // Inject before resampling: synthesize at the live capture rate, then follow
        // the normal resampler → accumulator → PCM16 → micHandler path.
        const sourceRate = (this.contextSampleRate > 0) ? this.contextSampleRate : TARGET_SAMPLE_RATE;
        const outChunk = Math.min(FRAME_SAMPLES, this.diagSignalSamplesLeft);
        const sourceChunk = Math.max(1, Math.round(sourceRate * (outChunk / TARGET_SAMPLE_RATE)));
        const samples = new Float32Array(sourceChunk);
        const twoPiF = (2 * Math.PI * (this.diagToneHz || 1000)) / sourceRate;
        const amp = this.diagToneAmplitude || 0.2;
        for (let i = 0; i < sourceChunk; i++) {
            samples[i] = Math.sin(this.diagSignalPhase) * amp;
            this.diagSignalPhase += twoPiF;
        }
        this.diagSignalSamplesLeft -= outChunk;

        this.#ensureResampler(sourceRate);
        const resampled = this.resampler.process(samples);

        for (let i = 0; i < resampled.length; i++) {
            this.speechRing[this.speechWrite] = 1;
            this.speechWrite = (this.speechWrite + 1) % this.speechRing.length;
            if (this.speechAvailable < this.speechRing.length) {
                this.speechAvailable++;
            } else {
                this.speechRead = (this.speechRead + 1) % this.speechRing.length;
            }
        }

        const frames = this.frameAccumulator.push(resampled);
        for (const frame of frames) {
            const pcm = new Int16Array(FRAME_SAMPLES);
            for (let i = 0; i < FRAME_SAMPLES; i++) {
                pcm[i] = floatToPcm16Sample(frame[i]);
            }
            if (pcm.byteLength === FRAME_BYTES && !this.muted) {
                this.outgoingFrames++;
                this.outgoingBytes += pcm.byteLength;
                this.diagSequence = (this.diagSequence || 0) + 1;
                Logger.debug(
                    `[Audio] stage=ws-send seq=${this.diagSequence} samples=${FRAME_SAMPLES} ` +
                    `bytes=${FRAME_BYTES} rate=${TARGET_SAMPLE_RATE} ch=1 gen=${this.activeMicGeneration}`
                );
                this.micHandler?.(pcm.buffer.slice(0));
            }
        }

        // Pace ≈20 ms between diagnostic chunks on the main thread.
        setTimeout(() => this.#pumpDiagnosticTone(), FRAME_SAMPLES / (TARGET_SAMPLE_RATE / 1000));
    }

    /**
     * @returns {{ echoCancellation: boolean, noiseSuppression: boolean, autoGainControl: boolean }}
     */
    getProcessingPrefs() {
        return { ...this.processing };
    }

    /**
     * Apply processing prefs and rebuild the mic graph exactly once when running.
     * @param {Partial<{ echoCancellation: boolean, noiseSuppression: boolean, autoGainControl: boolean }>} next
     */
    async setProcessingPrefs(next) {
        this.processing = {
            echoCancellation: next.echoCancellation ?? this.processing.echoCancellation,
            noiseSuppression: next.noiseSuppression ?? this.processing.noiseSuppression,
            autoGainControl: next.autoGainControl ?? this.processing.autoGainControl
        };
        this.#saveProcessingPrefs();
        if (this.isMicRunning()) {
            await this.rebuildMicPipeline();
        }
    }

    async resetProcessingPrefs() {
        this.processing = {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
        };
        this.#saveProcessingPrefs();
        if (this.isMicRunning()) {
            await this.rebuildMicPipeline();
        }
    }

    /**
     * Controlled single-generation mic pipeline replacement (device / constraint change).
     * Queues at most one follow-up rebuild so rapid toggles never overlap or drop prefs.
     * @param {string} [deviceId]
     */
    async rebuildMicPipeline(deviceId) {
        if (this.micRebuildInFlight) {
            this.micRebuildQueued = true;
            if (deviceId != null) {
                this.micRebuildQueuedDeviceId = String(deviceId);
            }
            Logger.debug("[Audio] rebuildMicPipeline queued — already in flight");
            return;
        }
        this.micRebuildInFlight = true;
        try {
            do {
                this.micRebuildQueued = false;
                const nextDevice = this.micRebuildQueuedDeviceId !== undefined
                    ? this.micRebuildQueuedDeviceId
                    : deviceId;
                this.micRebuildQueuedDeviceId = undefined;
                if (nextDevice != null) {
                    this.selectedDeviceId = String(nextDevice);
                }
                try {
                    await this.startMic(this.selectedDeviceId || undefined);
                } catch (err) {
                    Logger.debug(`[Audio] rebuildMicPipeline failed: ${err?.message || err}`);
                    throw err;
                }
                deviceId = undefined;
            } while (this.micRebuildQueued);
        } finally {
            this.micRebuildInFlight = false;
        }
    }

    #loadProcessingPrefs() {
        const defaults = {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
        };
        try {
            const raw = localStorage.getItem(SvgAudio.AUDIO_SETTINGS_KEY);
            if (!raw) return defaults;
            const parsed = JSON.parse(raw);
            if (!parsed || parsed.version !== 1) return defaults;
            return {
                echoCancellation: parsed.echoCancellation !== false,
                noiseSuppression: parsed.noiseSuppression !== false,
                autoGainControl: parsed.autoGainControl !== false
            };
        } catch {
            return defaults;
        }
    }

    #saveProcessingPrefs() {
        try {
            localStorage.setItem(
                SvgAudio.AUDIO_SETTINGS_KEY,
                JSON.stringify({ version: 1, ...this.processing })
            );
        } catch {
            // ignore quota / private mode
        }
    }

    /**
     * @param {MessageEvent} event
     * @param {number} generation
     */
    #handleMicMessage(event, generation) {
        if (generation !== this.activeMicGeneration) {
            return;
        }

        const { samples, speech, sampleRate: workletRate, channels, energy } = event.data || {};
        if (!(samples instanceof Float32Array) || samples.length === 0) {
            return;
        }

        // Worklet sampleRate is authoritative for captured PCM timing.
        if (Number.isFinite(workletRate) && workletRate > 0 && workletRate !== this.contextSampleRate) {
            this.contextSampleRate = workletRate;
            this.audioRuntime.sampleRate = workletRate;
            this.#ensureResampler(workletRate);
        } else {
            this.#ensureResampler(this.contextSampleRate);
        }

        if (Number.isFinite(channels) && channels > 0) {
            this.contextChannelCount = channels | 0;
        }

        const now = performance.now();
        const mode = this.getTransmitMode();
        const pttActive = this.isPttActive();

        const mono = samples;
        const resampled = this.resampler.process(mono);

        const speechValue = speech ? 1 : 0;
        for (let i = 0; i < resampled.length; i++) {
            this.speechRing[this.speechWrite] = speechValue;
            this.speechWrite = (this.speechWrite + 1) % this.speechRing.length;
            if (this.speechAvailable < this.speechRing.length) {
                this.speechAvailable++;
            } else {
                this.speechRead = (this.speechRead + 1) % this.speechRing.length;
            }
        }

        const level = Number.isFinite(energy) ? Math.min(1, Math.sqrt(Math.max(0, energy)) * 8) : 0;
        this.lastLevel = speech || level > 0.02 ? Math.max(level, this.lastLevel * 0.85) : this.lastLevel * 0.9;
        this.#updateLevelMeter(this.lastLevel);

        if (this.micIndicator) {
            if (mode === "ptt") {
                this.micIndicator.classList.toggle("active", !this.muted && pttActive);
            } else {
                if (speech && !this.muted) {
                    this.micActiveUntil = now + SvgAudio.MIC_HOLD_MS;
                }
                this.micIndicator.classList.toggle("active", now < this.micActiveUntil);
            }
        }

        const frames = this.frameAccumulator.push(resampled);
        for (const frame of frames) {
            let packetHasSpeech = false;
            for (let i = 0; i < FRAME_SAMPLES; i++) {
                if (this.speechAvailable > 0) {
                    if (this.speechRing[this.speechRead] !== 0) {
                        packetHasSpeech = true;
                    }
                    this.speechRead = (this.speechRead + 1) % this.speechRing.length;
                    this.speechAvailable--;
                }
            }

            if (this.shouldSendPacket(mode, packetHasSpeech, pttActive)) {
                // Legacy shape: Int16Array(960).buffer → exactly 1920 PCM16LE bytes.
                const pcm = new Int16Array(FRAME_SAMPLES);
                for (let i = 0; i < FRAME_SAMPLES; i++) {
                    pcm[i] = floatToPcm16Sample(frame[i]);
                }
                if (pcm.byteLength !== FRAME_BYTES) {
                    Logger.debug(`[Audio] rejecting malformed frame bytes=${pcm.byteLength}`);
                    continue;
                }
                this.outgoingFrames++;
                this.outgoingBytes += pcm.byteLength;
                this.micHandler?.(pcm.buffer);
            }
        }

        if (now - this.lastDiagAt >= SvgAudio.DIAG_EVERY_MS) {
            this.lastDiagAt = now;
            const ratio = this.resampler ? this.resampler.ratio : 1;
            const fps = this.outgoingFrames / (SvgAudio.DIAG_EVERY_MS / 1000);
            Logger.debug(
                `[Audio] diag pipeline=${this.pipelineId} inRate=${this.contextSampleRate} ` +
                `channels=${this.contextChannelCount} ratio=${ratio.toFixed(6)} ` +
                `frames=${this.outgoingFrames} bytes/frame=${FRAME_BYTES} ~fps=${fps.toFixed(1)} ` +
                `pending=${this.frameAccumulator.pendingSamples()}`
            );
            this.outgoingFrames = 0;
            this.outgoingBytes = 0;
        }
    }

    #updateLevelMeter(level) {
        if (!this.levelMeterEl) return;
        const pct = Math.max(0, Math.min(100, Math.round(level * 100)));
        this.levelMeterEl.style.setProperty("--level", `${pct}%`);
        this.levelMeterEl.setAttribute("aria-valuenow", String(pct));
    }

    shouldSendPacket(mode, speech, pttActive) {
        if (this.muted) return false;
        if (this.vadBypass || this.diagSignalActive) return true;
        if (mode === "voice") return speech;
        if (mode === "ptt") return pttActive;
        return false;
    }

    stopMic() {
        this.activeMicGeneration = 0;

        if (this.micNode) {
            this.micNode.port.onmessage = null;
            try {
                this.micNode.disconnect();
            } catch {
                // ignore
            }
            this.micNode = null;
        }

        if (this.micSilentGain) {
            try {
                this.micSilentGain.disconnect();
            } catch {
                // ignore
            }
            this.micSilentGain = null;
        }

        if (this.micSource) {
            try {
                this.micSource.disconnect();
            } catch {
                // ignore
            }
            this.micSource = null;
        }

        if (this.microphoneStream) {
            this.microphoneStream.getTracks().forEach((t) => t.stop());
            this.microphoneStream = null;
        }

        this.resampler?.reset();
        this.frameAccumulator.reset();
        this.speechWrite = 0;
        this.speechRead = 0;
        this.speechAvailable = 0;
        this.speechRing.fill(0);
        this.lastLevel = 0;
        this.#updateLevelMeter(0);

        if (this.micIndicator) {
            this.micIndicator.classList.remove("active");
        }
    }

    toggleMute() {
        this.muted = !this.muted;

        if (this.muted && this.micIndicator) {
            this.micIndicator.classList.remove("active");
        }

        return this.muted;
    }

    playAudio(buffer) {
        if (!this.audioWorkletNode) {
            return;
        }

        if (buffer instanceof Float32Array) {
            this.audioWorkletNode.port.postMessage({ type: "pcm", buffer: { samples: buffer, channels: 1 } });
            return;
        }

        const packet = this.#normalizeAudioPacket(buffer);
        if (!packet) {
            return;
        }
        this.audioWorkletNode.port.postMessage({ type: "pcm", buffer: packet });
    }

    resetAudioState() {
        this.audioWorkletNode?.port.postMessage({ type: "reset" });
        this.stopMic();
    }

    #normalizeAudioPacket(input) {
        if (!input || typeof input !== "object") {
            return null;
        }

        const samples = input.samples instanceof Float32Array ? input.samples : null;
        if (!samples) {
            return null;
        }

        const channels = Number.isFinite(input.channels) ? input.channels : 1;
        const safeChannels = channels === 2 ? 2 : 1;

        return { samples, channels: safeChannels };
    }

    async getAudioDevices() {
        if (!this.audioRuntime.mediaDevicesSupported) {
            return {
                microphones: [],
                speakers: [],
                available: false,
                reason: "Media devices are unavailable in this browser/context."
            };
        }

        let permissionStream = null;

        try {
            permissionStream = await navigator.mediaDevices.getUserMedia({
                audio: true
            });

            const devices = await navigator.mediaDevices.enumerateDevices();

            return {
                microphones: devices.filter((device) => device.kind === "audioinput"),
                speakers: devices.filter((device) => device.kind === "audiooutput"),
                available: true,
                reason: ""
            };
        } catch (error) {
            console.warn("Microphone permission denied:", error);

            return {
                microphones: [],
                speakers: [],
                available: false,
                reason: "Microphone permission denied or unavailable."
            };
        } finally {
            permissionStream?.getTracks().forEach((track) => track.stop());
        }
    }

    async setOutputDevice(deviceId) {
        if (this.audioContext?.setSinkId) {
            try {
                await this.audioContext.setSinkId(deviceId);
                Logger.log(`AudioContext output set to device ${deviceId}`);
                return true;
            } catch {
                Logger.log("Failed to set audio context sink ID, falling back to audio element");
            }
        } else {
            Logger.log("AudioContext does not support setSinkId, falling back to audio element");
        }

        if (window.audioElement?.setSinkId) {
            try {
                await window.audioElement.setSinkId(deviceId);
                Logger.log(`AudioElement output set to device ${deviceId}`);
                return true;
            } catch {
                Logger.log("Failed to set audio context sink ID");
            }
        } else {
            Logger.log("Audio element does not support setSinkId, cannot set output device");
        }
        Logger.log("No method available to set audio output device");

        return false;
    }

    getAudioRuntime() {
        return { ...this.audioRuntime };
    }
}
