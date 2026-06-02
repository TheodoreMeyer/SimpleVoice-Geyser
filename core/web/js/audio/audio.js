import {log} from "../utils/logger.js";

let audioContext;
let audioWorkletNode;

let micHandler = null;

let microphoneStream = null;
let micNode = null;
let micSource = null;

let muted = false;
let micActiveUntil = 0;
const MIC_HOLD_MS = 120;

const PACKET_SIZE = 960;
const BUFFER_SIZE = 960 * 20;

let micBuffer = new Int16Array(BUFFER_SIZE);
let speechBuffer = new Uint8Array(BUFFER_SIZE);
let writeIndex = 0;
let readIndex = 0;
let available = 0;

let micIndicator = null;
let isPttActive = () => true;
let getTransmitMode = () => "voice";
const TX_CODEC_PCM16 = 0;
const TX_CODEC_OPUS = 1;

let micOpusEncoder = null;
let micOpusReady = false;
let micOpusInitError = null;

const audioRuntime = {
    audioContextSupported: false,
    workletSupported: false,
    mediaDevicesSupported: false,
    canCaptureMic: false,
    canEncodeOpus: false,
    canSelectOutput: false,
    degradedReason: ""
};

function getAudioContextCtor() {
    return window.AudioContext || window.webkitAudioContext || null;
}

function resolveAudioModuleUrl(moduleName) {
    return new URL(moduleName, import.meta.url).href;
}

export async function initAudio() {
    const AudioContextCtor = getAudioContextCtor();
    audioRuntime.audioContextSupported = !!AudioContextCtor;
    audioRuntime.mediaDevicesSupported = !!(navigator.mediaDevices
        && typeof navigator.mediaDevices.getUserMedia === "function"
        && typeof navigator.mediaDevices.enumerateDevices === "function");

    window.audioElement = document.createElement("audio");
    audioRuntime.canSelectOutput = !!window.audioElement?.setSinkId;

    if (!AudioContextCtor) {
        audioRuntime.degradedReason = "AudioContext is unavailable in this browser.";
        log(`[Audio] ${audioRuntime.degradedReason}`);
        return { ...audioRuntime };
    }

    audioContext = new AudioContextCtor({ sampleRate: 48000 });

    if (audioContext.sampleRate !== 48000) {
        console.warn("WRONG SAMPLE RATE:", audioContext.sampleRate);
    }

    audioRuntime.workletSupported = !!(audioContext.audioWorklet && typeof AudioWorkletNode !== "undefined");
    if (!audioRuntime.workletSupported) {
        audioRuntime.degradedReason = "AudioWorklet is unavailable, receive/mic processing is limited.";
        log(`[Audio] ${audioRuntime.degradedReason}`);
        audioRuntime.canCaptureMic = false;
        audioRuntime.canSelectOutput = audioRuntime.canSelectOutput || !!audioContext.setSinkId;
        return { ...audioRuntime };
    }

    try {
        await audioContext.audioWorklet.addModule(resolveAudioModuleUrl("speaker.js"));
        await audioContext.audioWorklet.addModule(resolveAudioModuleUrl("microphone.js"));

        audioWorkletNode = new AudioWorkletNode(audioContext, "pcm-player", {
            numberOfInputs: 0,
            numberOfOutputs: 1,
            outputChannelCount: [2]
        });
        audioWorkletNode.connect(audioContext.destination);
    } catch (error) {
        audioRuntime.degradedReason = "Failed loading audio worklets.";
        log(`[Audio] ${audioRuntime.degradedReason}`);
        console.error(error);
        audioWorkletNode = null;
    }

    audioRuntime.canCaptureMic = !!audioWorkletNode && audioRuntime.mediaDevicesSupported;
    audioRuntime.canSelectOutput = audioRuntime.canSelectOutput || !!audioContext.setSinkId;

    if (!audioRuntime.canCaptureMic && !audioRuntime.degradedReason) {
        audioRuntime.degradedReason = "Microphone capture is unavailable in this browser/context.";
    }

    return { ...audioRuntime };
}

export function setMicIndicator(el) {
    micIndicator = el;
}

export function onMicData(handler) {
    micHandler = handler;
}

export function setTransmitModeProvider(fn) {
    getTransmitMode = fn;
}

export function setPttActiveProvider(fn) {
    isPttActive = fn;
}

export async function startMic(deviceId) {
    if (!audioRuntime.canCaptureMic || !audioContext) {
        throw new Error("Microphone capture is not supported in this browser/context.");
    }

    await audioContext.resume();

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

    microphoneStream = await navigator.mediaDevices.getUserMedia({
        audio: audioConstraints
    });

    micSource = audioContext.createMediaStreamSource(microphoneStream);
    micNode = new AudioWorkletNode(audioContext, "mic-capture");

    micSource.connect(micNode);
    micNode.port.onmessage = handleMicMessage;

    await initMicOpusEncoder();
    audioRuntime.canEncodeOpus = micOpusReady;
}

function handleMicMessage(event) {
    const { samples, speech } = event.data;
    const now = performance.now();
    const speechValue = speech ? 1 : 0;

    const mode = getTransmitMode();
    const pttActive = isPttActive();

    for (let i = 0; i < samples.length; i++) {
        const s = Math.max(-1, Math.min(1, samples[i]));
        micBuffer[writeIndex] = s < 0 ? s * 0x8000 : s * 0x7fff;
        speechBuffer[writeIndex] = speechValue;

        writeIndex = (writeIndex + 1) % BUFFER_SIZE;

        if (available < BUFFER_SIZE) {
            available++;
        } else {
            readIndex = (readIndex + 1) % BUFFER_SIZE;
        }
    }

    if (micIndicator) {
        if (mode === "ptt") {
            micIndicator.classList.toggle("active", !muted && pttActive);
        } else {
            if (speech && !muted) {
                micActiveUntil = now + MIC_HOLD_MS;
            }
            micIndicator.classList.toggle("active", now < micActiveUntil);
        }
    }

    while (available >= PACKET_SIZE) {
        const packet = new Int16Array(PACKET_SIZE);
        let packetHasSpeech = false;

        for (let i = 0; i < PACKET_SIZE; i++) {
            packet[i] = micBuffer[readIndex];
            if (speechBuffer[readIndex] !== 0) {
                packetHasSpeech = true;
            }
            readIndex = (readIndex + 1) % BUFFER_SIZE;
        }

        available -= PACKET_SIZE;

        if (shouldSendPacket(mode, packetHasSpeech, pttActive)) {
            sendMicPacket(packet);
        }
    }
}

async function initMicOpusEncoder() {
    if (micOpusReady || micOpusInitError) {
        return;
    }
    try {
        const mod = await import("https://cdn.jsdelivr.net/npm/opus-encoder@0.7.3/+esm");
        const OpusEncoder = mod?.default || mod?.OpusEncoder;
        if (!OpusEncoder) {
            throw new Error("OpusEncoder export not found");
        }
        micOpusEncoder = new OpusEncoder(48000, 1);
        micOpusReady = true;
        micOpusInitError = null;
        log("[AudioTX] Opus WASM encoder ready.");
    } catch (err) {
        micOpusReady = false;
        micOpusInitError = err?.message || String(err);
        log(`[AudioTX] Opus WASM encoder unavailable, using PCM fallback: ${micOpusInitError}`);
    }
}

function sendMicPacket(packet) {
    if (micOpusReady && micOpusEncoder) {
        try {
            const encoded = micOpusEncoder.encode(packet);
            if (encoded && encoded.length > 0) {
                const payload = encoded instanceof Uint8Array ? encoded : new Uint8Array(encoded);
                const frame = new Uint8Array(payload.length + 1);
                frame[0] = TX_CODEC_OPUS;
                frame.set(payload, 1);
                micHandler?.(frame.buffer);
                return;
            }
        } catch (err) {
            log(`[AudioTX] Opus encode failed, fallback to PCM: ${err?.message || err}`);
        }
    }

    const pcmBytes = new Uint8Array(packet.buffer.slice(0));
    const frame = new Uint8Array(pcmBytes.length + 1);
    frame[0] = TX_CODEC_PCM16;
    frame.set(pcmBytes, 1);
    micHandler?.(frame.buffer);
}

function shouldSendPacket(mode, speech, pttActive) {
    if (muted) return false;
    if (mode === "voice") return speech;
    if (mode === "ptt") return pttActive;
    return false;
}

export function stopMic() {
    if (micNode) {
        micNode.port.onmessage = null;
        micNode.disconnect();
        micNode = null;
    }

    if (micSource) {
        micSource.disconnect();
        micSource = null;
    }

    if (microphoneStream) {
        microphoneStream.getTracks().forEach(t => t.stop());
        microphoneStream = null;
    }

    writeIndex = 0;
    readIndex = 0;
    available = 0;
    speechBuffer.fill(0);
    if (micOpusEncoder && typeof micOpusEncoder.reset === "function") {
        micOpusEncoder.reset();
    }

    if (micIndicator) {
        micIndicator.classList.remove("active");
    }
}

export function toggleMute() {
    muted = !muted;

    if (muted && micIndicator) {
        micIndicator.classList.remove("active");
    }

    return muted;
}

export function playAudio(buffer) {
    if (!audioWorkletNode) {
        return;
    }

    if (buffer instanceof Float32Array) {
        audioWorkletNode.port.postMessage({ type: "pcm", buffer: { samples: buffer, channels: 1 } });
        return;
    }

    const packet = normalizeAudioPacket(buffer);
    if (!packet) {
        return;
    }
    audioWorkletNode.port.postMessage({ type: "pcm", buffer: packet });
}

export function resetAudioState() {
    audioWorkletNode?.port.postMessage({ type: "reset" });

    writeIndex = 0;
    readIndex = 0;
    available = 0;
    speechBuffer.fill(0);
}

function normalizeAudioPacket(input) {
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

export async function getAudioDevices() {
    if (!audioRuntime.mediaDevicesSupported) {
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

export async function setOutputDevice(deviceId) {
    if (audioContext?.setSinkId) {
        try {
            await audioContext.setSinkId(deviceId);
            log(`AudioContext output set to device ${deviceId}`);
            return true;
        } catch {
            log("Failed to set audio context sink ID, falling back to audio element");
        }
    } else {
        log("AudioContext does not support setSinkId, falling back to audio element");
    }

    if (window.audioElement?.setSinkId) {
        try {
            await window.audioElement.setSinkId(deviceId);
            log(`AudioElement output set to device ${deviceId}`);
            return true;
        } catch {
            log("Failed to set audio context sink ID");
        }
    } else {
        log("Audio element does not support setSinkId, cannot set output device");
    }
    log("No method available to set audio output device");

    return false;
}

export function getAudioRuntime() {
    return {
        ...audioRuntime,
        micOpusError: micOpusInitError
    };
}
