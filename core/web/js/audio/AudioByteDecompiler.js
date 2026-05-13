import {log} from "../utils/logger.js";

const MAGIC_0 = 0x53; // 'S'
const MAGIC_1 = 0x56; // 'V'
const VERSION_2 = 2;
const HEADER_SIZE = 20;
const CODEC_OPUS = 1;
const CODEC_PCM16_LE = 2;

let wasmDecoder = null;
let wasmReady = false;
let wasmInitError = null;
let decompilerInitPromise = null;

let rxSvgV2Frames = 0;
let rxSvgV2Malformed = 0;
let rxSvgV2DecodeErrors = 0;

let webCodecsProbeDone = false;
let webCodecsSupported = false;
let webCodecsProbeError = null;

let webCodecDecoder = null;
let webCodecQueue = [];
let webCodecTimestampUs = 0;

function supportsWebCodecsPrimitives() {
    return typeof AudioDecoder !== "undefined"
        && typeof EncodedAudioChunk !== "undefined"
        && typeof AudioData !== "undefined";
}

async function probeWebCodecsSupport() {
    if (webCodecsProbeDone) {
        return webCodecsSupported;
    }
    webCodecsProbeDone = true;
    if (!window.isSecureContext || !supportsWebCodecsPrimitives()) {
        webCodecsSupported = false;
        return false;
    }
    try {
        const result = await AudioDecoder.isConfigSupported({
            codec: "opus",
            sampleRate: 48000,
            numberOfChannels: 1
        });
        webCodecsSupported = !!result?.supported;
    } catch (err) {
        webCodecsProbeError = err?.message || String(err);
        webCodecsSupported = false;
    }
    return webCodecsSupported;
}

async function initWasmDecoder() {
    if (wasmReady || wasmInitError) {
        return;
    }
    try {
        const mod = await import("https://cdn.jsdelivr.net/npm/opus-decoder@0.7.11/+esm");
        const OpusDecoder = mod?.OpusDecoder;
        if (!OpusDecoder) {
            throw new Error("OpusDecoder export not found");
        }
        wasmDecoder = new OpusDecoder({
            sampleRate: 48000,
            channels: 1
        });
        await wasmDecoder.ready;
        wasmReady = true;
        log("[AudioRX] Opus WASM decoder ready.");
    } catch (err) {
        wasmInitError = err?.message || String(err);
        log(`[AudioRX] Opus WASM decoder unavailable: ${wasmInitError}`);
    }
}

export async function warmupAudioDecompiler() {
    if (!decompilerInitPromise) {
        decompilerInitPromise = Promise.all([
            initWasmDecoder(),
            probeWebCodecsSupport()
        ]).catch(() => {
            // Keep runtime alive even if one probe fails.
        });
    }
    await decompilerInitPromise;
}

export async function getAudioCapabilities() {
    await warmupAudioDecompiler();
    return {
        secureContext: !!window.isSecureContext,
        supportsLegacy: true,
        supportsSvgV2: wasmReady || webCodecsSupported,
        supportsOpusDecoder: wasmReady || webCodecsSupported,
        decoder: {
            opusWasm: wasmReady,
            webCodecs: webCodecsSupported,
            wasmError: wasmInitError,
            webCodecsError: webCodecsProbeError
        }
    };
}

export function getAudioDecompileStats() {
    return {
        frames: rxSvgV2Frames,
        malformed: rxSvgV2Malformed,
        decodeErrors: rxSvgV2DecodeErrors
    };
}

export async function decodeSvgV2Frame(arrayBuffer) {
    const frame = parseFrame(arrayBuffer);
    if (!frame) {
        return null;
    }
    if (frame.malformed) {
        rxSvgV2Malformed++;
        return {
            format: "svg-v2",
            malformed: true,
            reason: frame.reason
        };
    }

    rxSvgV2Frames++;

    try {
        if (!frame.payload || frame.payload.length === 0) {
            return {
                format: "svg-v2",
                packet: { samples: new Float32Array(0), channels: 2 },
                meta: frame
            };
        }

        if (frame.codec === CODEC_OPUS) {
            const decoded = await decodeOpus(frame.payload);
            if (!decoded) {
                throw new Error("No Opus decoder available");
            }
            const packet = applySpatial(decoded, frame.pan, frame.gain);
            return {
                format: "svg-v2",
                packet,
                meta: frame
            };
        }
        if (frame.codec === CODEC_PCM16_LE) {
            return {
                format: "svg-v2",
                packet: decodePcm16Payload(frame.payload, frame.channels, frame.pan, frame.gain),
                meta: frame
            };
        }
        throw new Error(`Unsupported codec id ${frame.codec}`);
    } catch (err) {
        rxSvgV2DecodeErrors++;
        return {
            format: "svg-v2",
            malformed: true,
            reason: err?.message || String(err)
        };
    }
}

function parseFrame(arrayBuffer) {
    const view = new DataView(arrayBuffer);
    if (view.byteLength < HEADER_SIZE) {
        return null;
    }

    const looksLikeV2 = view.getUint8(0) === MAGIC_0
        && view.getUint8(1) === MAGIC_1
        && view.getUint8(2) === VERSION_2;
    if (!looksLikeV2) {
        return null;
    }

    const payloadLength = view.getUint32(16, true);
    if (payloadLength > view.byteLength - HEADER_SIZE) {
        return { malformed: true, reason: "payload length exceeds frame size" };
    }

    const payload = new Uint8Array(arrayBuffer, HEADER_SIZE, payloadLength);
    const panRaw = view.getInt16(8, true);
    const gainRaw = view.getInt16(10, true);
    const sampleRate = view.getUint16(12, true);
    const channels = Math.max(1, view.getUint8(14));

    return {
        malformed: false,
        flags: view.getUint8(3),
        sequence: view.getUint32(4, true),
        pan: Math.max(-1, Math.min(1, panRaw / 32767)),
        gain: Math.max(0, Math.min(1, gainRaw / 32767)),
        sampleRate: sampleRate > 0 ? sampleRate : 48000,
        channels,
        codec: view.getUint8(15),
        payload
    };
}

async function decodeOpus(opusPayload) {
    await warmupAudioDecompiler();

    if (wasmReady && wasmDecoder) {
        const decoded = wasmDecoder.decodeFrame(opusPayload);
        const channelData = decoded?.channelData;
        if (!Array.isArray(channelData) || channelData.length === 0) {
            throw new Error("WASM decode returned empty channel data");
        }
        return channelData;
    }

    if (webCodecsSupported) {
        return await decodeWithWebCodecs(opusPayload);
    }
    return null;
}

async function decodeWithWebCodecs(opusPayload) {
    if (!webCodecDecoder) {
        webCodecDecoder = new AudioDecoder({
            output: (audioData) => {
                try {
                    webCodecQueue.push(audioDataToChannelData(audioData));
                } finally {
                    audioData.close();
                }
            },
            error: (err) => {
                log(`[AudioRX] WebCodecs decode error: ${err?.message || err}`);
            }
        });
        webCodecDecoder.configure({
            codec: "opus",
            sampleRate: 48000,
            numberOfChannels: 1
        });
    }

    const durationUs = 20_000;
    const chunk = new EncodedAudioChunk({
        type: "key",
        timestamp: webCodecTimestampUs,
        duration: durationUs,
        data: opusPayload
    });
    webCodecTimestampUs += durationUs;

    webCodecDecoder.decode(chunk);
    await webCodecDecoder.flush();

    if (webCodecQueue.length === 0) {
        throw new Error("WebCodecs did not emit decoded frames");
    }
    return webCodecQueue.shift();
}

function audioDataToChannelData(audioData) {
    const channels = audioData.numberOfChannels || 1;
    const frames = audioData.numberOfFrames || 0;
    const planeCount = audioData.numberOfChannels || 1;

    const out = [];
    if (planeCount >= channels) {
        for (let c = 0; c < channels; c++) {
            const plane = new Float32Array(frames);
            audioData.copyTo(plane, { planeIndex: c });
            out.push(plane);
        }
        return out;
    }

    const interleaved = new Float32Array(frames * channels);
    audioData.copyTo(interleaved);
    for (let c = 0; c < channels; c++) {
        const plane = new Float32Array(frames);
        for (let i = 0; i < frames; i++) {
            plane[i] = interleaved[i * channels + c] || 0;
        }
        out.push(plane);
    }
    return out;
}

function applySpatial(channelData, pan, gain) {
    const leftIn = channelData[0] || new Float32Array(0);
    const rightIn = channelData[1] || leftIn;
    const frameCount = Math.min(leftIn.length, rightIn.length || leftIn.length);

    const theta = (Math.max(-1, Math.min(1, pan)) + 1) * (Math.PI / 4);
    const leftPanGain = Math.cos(theta);
    const rightPanGain = Math.sin(theta);
    const overallGain = Math.max(0, Math.min(1, gain));

    const out = new Float32Array(frameCount * 2);
    for (let i = 0; i < frameCount; i++) {
        const mono = (leftIn[i] + rightIn[i]) * 0.5;
        out[i * 2] = mono * leftPanGain * overallGain;
        out[i * 2 + 1] = mono * rightPanGain * overallGain;
    }
    return { samples: out, channels: 2 };
}

function decodePcm16Payload(payload, channels, pan, gain) {
    const view = new DataView(payload.buffer, payload.byteOffset, payload.byteLength);
    const sampleCount = Math.floor(view.byteLength / 2);
    const samples = new Float32Array(sampleCount);
    for (let i = 0; i < sampleCount; i++) {
        samples[i] = view.getInt16(i * 2, true) / 32768;
    }

    if (channels === 2) {
        const frameCount = Math.floor(samples.length / 2);
        const out = new Float32Array(frameCount * 2);
        const overallGain = Math.max(0, Math.min(1, gain));
        const theta = (Math.max(-1, Math.min(1, pan)) + 1) * (Math.PI / 4);
        const leftPanGain = Math.cos(theta);
        const rightPanGain = Math.sin(theta);
        for (let i = 0; i < frameCount; i++) {
            const mono = (samples[i * 2] + samples[i * 2 + 1]) * 0.5;
            out[i * 2] = mono * leftPanGain * overallGain;
            out[i * 2 + 1] = mono * rightPanGain * overallGain;
        }
        return { samples: out, channels: 2 };
    }

    const monoChannels = [samples];
    return applySpatial(monoChannels, pan, gain);
}
