import {Logger} from "../utils/logger.js";

export class SvgAudio {

    static MIC_HOLD_MS = 120;
    static PACKET_SIZE = 960;
    static BUFFER_SIZE = SvgAudio.PACKET_SIZE * 20;

    audioContext;

    audioWorkletNode;

    constructor() {
        this.micHandler = null;
        this.microphoneStream = null;
        this.micNode = null;
        this.micSource = null;
        this.muted = false;
        this.micActiveUntil = 0;
        this.micBuffer = new Int16Array(SvgAudio.BUFFER_SIZE);
        this.speechBuffer = new Uint8Array(SvgAudio.BUFFER_SIZE);
        this.writeIndex = 0;
        this.readIndex = 0;
        this.available = 0;
        this.micIndicator = null;
        this.isPttActive = () => true;
        this.getTransmitMode = () => "voice";

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

        this.audioRuntime.canCaptureMic = !!this.audioWorkletNode && this.audioRuntime.mediaDevicesSupported;
        this.audioRuntime.canSelectOutput = this.audioRuntime.canSelectOutput || !!this.audioContext.setSinkId;

        if (!this.audioRuntime.canCaptureMic && !this.audioRuntime.degradedReason) {
            this.audioRuntime.degradedReason = "Microphone capture is unavailable in this browser/context.";
        }

        return { ...this.audioRuntime };
    }

    setMicIndicator(el) {
        this.micIndicator = el;
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

    async startMic(deviceId) {
        if (!this.audioRuntime.canCaptureMic || !this.audioContext) {
            throw new Error("Microphone capture is not supported in this browser/context.");
        }

        await this.audioContext.resume();

        const audioConstraints = {
            noiseSuppression: true,
            echoCancellation: true,
            autoGainControl: true,
            channelCount: 1,
            sampleRate: 48000
        };
        if (deviceId) {
            audioConstraints.deviceId = { exact: deviceId };
        }

        this.microphoneStream = await navigator.mediaDevices.getUserMedia({
            audio: audioConstraints
        });

        this.micSource = this.audioContext.createMediaStreamSource(this.microphoneStream);
        this.micNode = new AudioWorkletNode(this.audioContext, "mic-capture");

        this.micSource.connect(this.micNode);
        this.micNode.port.onmessage = (event) => this.#handleMicMessage(event);
    }

    #handleMicMessage(event) {
        const { samples, speech } = event.data;
        const now = performance.now();
        const speechValue = speech ? 1 : 0;

        const mode = this.getTransmitMode();
        const pttActive = this.isPttActive();

        for (let i = 0; i < samples.length; i++) {
            const s = Math.max(-1, Math.min(1, samples[i]));
            this.micBuffer[this.writeIndex] = s < 0 ? s * 0x8000 : s * 0x7fff;
            this.speechBuffer[this.writeIndex] = speechValue;

            this.writeIndex = (this.writeIndex + 1) % SvgAudio.BUFFER_SIZE;

            if (this.available < SvgAudio.BUFFER_SIZE) {
                this.available++;
            } else {
                this.readIndex = (this.readIndex + 1) % SvgAudio.BUFFER_SIZE;
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

        while (this.available >= SvgAudio.PACKET_SIZE) {
            const packet = new Int16Array(SvgAudio.PACKET_SIZE);
            let packetHasSpeech = false;

            for (let i = 0; i < SvgAudio.PACKET_SIZE; i++) {
                packet[i] = this.micBuffer[this.readIndex];
                if (this.speechBuffer[this.readIndex] !== 0) {
                    packetHasSpeech = true;
                }
                this.readIndex = (this.readIndex + 1) % SvgAudio.BUFFER_SIZE;
            }

            this.available -= SvgAudio.PACKET_SIZE;

            if (this.shouldSendPacket(mode, packetHasSpeech, pttActive)) {
                this.micHandler?.(packet.slice().buffer);
            }
        }
    }

    shouldSendPacket(mode, speech, pttActive) {
        if (this.muted) return false;
        if (mode === "voice") return speech;
        if (mode === "ptt") return pttActive;
        return false;
    }

    stopMic() {
        if (this.micNode) {
            this.micNode.port.onmessage = null;
            this.micNode.disconnect();
            this.micNode = null;
        }

        if (this.micSource) {
            this.micSource.disconnect();
            this.micSource = null;
        }

        if (this.microphoneStream) {
            this.microphoneStream.getTracks().forEach(t => t.stop());
            this.microphoneStream = null;
        }

        this.writeIndex = 0;
        this.readIndex = 0;
        this.available = 0;
        this.speechBuffer.fill(0);

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

        this.writeIndex = 0;
        this.readIndex = 0;
        this.available = 0;
        this.speechBuffer.fill(0);
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
}
