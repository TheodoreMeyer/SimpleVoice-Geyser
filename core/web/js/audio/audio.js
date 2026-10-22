import {Logger} from "../utils/logger.js";

export class SvgAudio {

    static MIC_HOLD_MS = 120;
    static DEFAULT_VAD_START = 0.00008;
    static DEFAULT_VAD_STOP = 0.00004;
    static DEFAULT_INPUT_GAIN = 1.0;
    /** Max software mic boost (browser AGC is off; quiet mics need headroom). */
    static MAX_INPUT_GAIN = 6.0;
    /** Throttle energy UI updates (~5/s assuming ~375 mic messages/sec). */
    static ENERGY_UPDATE_EVERY = 75;

    audioContext;

    audioWorkletNode;

    constructor() {
        this.micHandler = null;
        this.microphoneStream = null;
        this.micNode = null;
        this.micSource = null;
        this.micGainNode = null;
        this.micStartPromise = null;
        this.micGeneration = 0;
        this.muted = false;
        this.micActiveUntil = 0;
        this.micIndicator = null;
        this.micTransmissionEnabled = false;
        this.isPttActive = () => true;
        this.getTransmitMode = () => "voice";

        this.vadStartThreshold = SvgAudio.DEFAULT_VAD_START;
        this.vadStopThreshold = SvgAudio.DEFAULT_VAD_STOP;
        this.inputGain = SvgAudio.DEFAULT_INPUT_GAIN;
        this.onEnergyUpdate = null;
        this.energyUpdateCount = 0;

        this.audioRuntime = {
            audioContextSupported: false,
            workletSupported: false,
            mediaDevicesSupported: false,
            canCaptureMic: false,
            canSelectOutput: false,
            degradedReason: ""
        };
    }

    getAudioContextCtor() {
        return window.AudioContext || window.webkitAudioContext || null;
    }

    resolveAudioModuleUrl(moduleName) {
        return new URL(moduleName, import.meta.url).href;
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

        this.audioContext = new AudioContextCtor({ sampleRate: 48000 });
        this.audioRuntime.sampleRate = this.audioContext.sampleRate;

        if (this.audioContext.sampleRate !== 48000) {
            console.warn("WRONG SAMPLE RATE:", this.audioContext.sampleRate);
        }

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

        this.audioRuntime.canCaptureMic = !!this.audioWorkletNode
            && this.audioRuntime.mediaDevicesSupported
            && this.audioContext.sampleRate === 48000;
        this.audioRuntime.canSelectOutput = this.audioRuntime.canSelectOutput || !!this.audioContext.setSinkId;

        if (!this.audioRuntime.canCaptureMic && !this.audioRuntime.degradedReason) {
            this.audioRuntime.degradedReason = this.audioContext.sampleRate !== 48000
                ? `Microphone capture requires 48000 Hz; this browser provides ${this.audioContext.sampleRate} Hz.`
                : "Microphone capture is unavailable in this browser/context.";
        }

        return { ...this.audioRuntime };
    }

    setMicIndicator(el) {
        this.micIndicator = el;
    }

    onMicData(handler) {
        this.micHandler = handler;
    }

    setMicTransmissionEnabled(enabled) {
        this.micTransmissionEnabled = enabled;
    }

    setTransmitModeProvider(fn) {
        this.getTransmitMode = fn;
    }

    setPttActiveProvider(fn) {
        this.isPttActive = fn;
    }

    setVadThreshold(start, stop) {
        if (Number.isFinite(start)) this.vadStartThreshold = start;
        if (Number.isFinite(stop)) this.vadStopThreshold = stop;
        this.#pushMicSettings();
    }

    setInputGain(gain) {
        if (!Number.isFinite(gain)) return;
        this.inputGain = Math.max(0.1, Math.min(SvgAudio.MAX_INPUT_GAIN, gain));
        if (this.micGainNode && this.audioContext) {
            // AudioParam is the reliable loudness path (does not depend on worklet messages).
            this.micGainNode.gain.setTargetAtTime(
                this.inputGain,
                this.audioContext.currentTime,
                0.015
            );
        }
        this.#pushMicSettings();
    }

    setEnergyCallback(fn) {
        this.onEnergyUpdate = typeof fn === "function" ? fn : null;
    }

    #pushMicSettings() {
        if (!this.micNode) return;
        this.micNode.port.postMessage({
            type: "setThreshold",
            start: this.vadStartThreshold,
            stop: this.vadStopThreshold
        });
        // Keep worklet gain at 1 — amplitude is applied by micGainNode in the graph.
        this.micNode.port.postMessage({
            type: "setGain",
            gain: 1
        });
    }

    async startMic(deviceId) {
        if (this.micNode) return;
        if (this.micStartPromise) return this.micStartPromise;
        if (!this.audioRuntime.canCaptureMic || !this.audioContext) {
            throw new Error("Microphone capture is not supported in this browser/context.");
        }

        const startup = this.#startMicCapture(deviceId, this.micGeneration);
        this.micStartPromise = startup;
        try {
            await startup;
        } finally {
            if (this.micStartPromise === startup) this.micStartPromise = null;
        }
    }

    async #startMicCapture(deviceId, generation) {
        let stream, source, gainNode, node;
        try {
            await this.audioContext.resume();
            if (generation !== this.micGeneration) return;

            // Browser AEC/NS/AGC can distort voice over WAN; keep raw capture instead.
            // Ported from https://github.com/TheodoreMeyer/SimpleVoice-Geyser/pull/52
            const audioConstraints = {
                noiseSuppression: false,
                echoCancellation: false,
                autoGainControl: false,
                channelCount: 1,
                sampleRate: 48000
            };
            if (deviceId) {
                audioConstraints.deviceId = { exact: deviceId };
            }

            stream = await navigator.mediaDevices.getUserMedia({audio: audioConstraints});
            if (generation !== this.micGeneration) {
                stream.getTracks().forEach(track => track.stop());
                return;
            }

            source = this.audioContext.createMediaStreamSource(stream);
            gainNode = this.audioContext.createGain();
            gainNode.gain.value = this.inputGain;
            node = new AudioWorkletNode(this.audioContext, "mic-capture");
            // source → GainNode (mic volume) → worklet (VAD / framing / soft-limit)
            source.connect(gainNode);
            gainNode.connect(node);
            node.port.onmessage = (event) => {
                if (generation === this.micGeneration) this.#handleMicMessage(event);
            };

            this.microphoneStream = stream;
            this.micSource = source;
            this.micGainNode = gainNode;
            this.micNode = node;
            this.#pushMicSettings();
        } catch (error) {
            node?.disconnect();
            gainNode?.disconnect();
            source?.disconnect();
            stream?.getTracks().forEach(track => track.stop());
            throw error;
        }
    }

    #handleMicMessage(event) {
        const { samples, speech, timestamp, energy } = event.data;
        const now = performance.now();
        const mode = this.getTransmitMode();
        const pttActive = this.isPttActive();

        if (this.onEnergyUpdate && energy !== undefined) {
            this.energyUpdateCount++;
            if (this.energyUpdateCount % SvgAudio.ENERGY_UPDATE_EVERY === 0) {
                this.onEnergyUpdate(energy);
            }
        }

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

        if (this.micTransmissionEnabled && this.shouldSendPacket(mode, speech, pttActive)) {
            this.micHandler?.({samples, speech, timestamp});
        }
    }

    shouldSendPacket(mode, speech, pttActive) {
        if (this.muted) return false;
        if (mode === "voice") return speech;
        if (mode === "ptt") return pttActive;
        return false;
    }

    stopMic() {
        this.micGeneration++;
        this.micStartPromise = null;
        if (this.micNode) {
            this.micNode.port.onmessage = null;
            this.micNode.disconnect();
            this.micNode = null;
        }

        if (this.micGainNode) {
            this.micGainNode.disconnect();
            this.micGainNode = null;
        }

        if (this.micSource) {
            this.micSource.disconnect();
            this.micSource = null;
        }

        if (this.microphoneStream) {
            this.microphoneStream.getTracks().forEach(t => t.stop());
            this.microphoneStream = null;
        }

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
            this.audioWorkletNode.port.postMessage({
                type: "pcm",
                buffer: { samples: buffer, channels: 1, senderId: "legacy" }
            });
            return;
        }

        const packet = this.#normalizeAudioPacket(buffer);
        if (!packet) {
            return;
        }
        this.audioWorkletNode.port.postMessage({ type: "pcm", buffer: packet });
    }

    resetAudioState() {
        this.stopMic();
        this.audioWorkletNode?.port.postMessage({ type: "reset" });

        this.micTransmissionEnabled = false;
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
        const senderId = typeof input.senderId === "string" && input.senderId
            ? input.senderId
            : "legacy";

        return { samples, channels: safeChannels, senderId };
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
                microphones: devices.filter(device => device.kind === "audioinput"),
                speakers: devices.filter(device => device.kind === "audiooutput"),
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
            permissionStream?.getTracks().forEach(track => track.stop());
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

    getAudioContextTimeUs() {
        return this.audioContext ? this.audioContext.currentTime * 1000000 : -Infinity;
    }
}
