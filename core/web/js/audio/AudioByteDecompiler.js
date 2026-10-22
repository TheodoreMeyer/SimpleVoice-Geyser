import {Logger} from "../utils/logger.js";

const MAGIC_0 = 0x53; // 'S'
const MAGIC_1 = 0x56; // 'V'
const VERSION_2 = 2;
const HEADER_SIZE = 20;
/** Optional talker UUID on server→browser frames (Adjust Volumes / per-sender decode). */
const SENDER_UUID_SIZE = 16;
const FLAG_HAS_SENDER = 0x08;
const CODEC_OPUS = 1;
const CODEC_PCM16_LE = 2;
const DECODER_IDLE_MS = 10_000;

/** @type {typeof import("opus-decoder").OpusDecoder|null} */
let OpusDecoderClass = null;
let wasmReady = false;
let wasmInitError = null;
let decompilerInitPromise = null;

/** @type {Map<string, { decoder: object, lastUsed: number }>} */
const decodersBySender = new Map();

let rxSvgV2Frames = 0;
let rxSvgV2Malformed = 0;
let rxSvgV2DecodeErrors = 0;

const webCodecsSupported = false;
const webCodecsProbeError = "disabled_by_policy_wasm_only";

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
        OpusDecoderClass = OpusDecoder;
        // Probe one instance so capability reporting stays accurate.
        const probe = new OpusDecoder({
            sampleRate: 48000,
            channels: 1
        });
        await probe.ready;
        decodersBySender.set("other", { decoder: probe, lastUsed: Date.now() });
        wasmReady = true;
        Logger.log("[AudioRX] Opus WASM decoder ready (per-sender pools).");
    } catch (err) {
        wasmInitError = err?.message || String(err);
        Logger.log(`[AudioRX] Opus WASM decoder unavailable: ${wasmInitError}`);
    }
}

export async function warmupAudioDecompiler() {
    if (!decompilerInitPromise) {
        decompilerInitPromise = Promise.all([
            initWasmDecoder()
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
        supportsSvgV2: wasmReady,
        supportsOpusDecoder: wasmReady,
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
        decodeErrors: rxSvgV2DecodeErrors,
        decoderCount: decodersBySender.size
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
                packet: { samples: new Float32Array(0), channels: 2, senderId: frame.senderUuid || "other" },
                meta: frame
            };
        }

        if (frame.codec === CODEC_OPUS) {
            const decoded = await decodeOpus(frame.payload, frame.senderUuid);
            if (!decoded) {
                throw new Error("No Opus decoder available");
            }
            const packet = applySpatial(decoded, frame.pan, frame.gain);
            packet.senderId = frame.senderUuid || "other";
            return {
                format: "svg-v2",
                packet,
                meta: frame
            };
        }
        if (frame.codec === CODEC_PCM16_LE) {
            const packet = decodePcm16Payload(frame.payload, frame.channels, frame.pan, frame.gain);
            packet.senderId = frame.senderUuid || "other";
            return {
                format: "svg-v2",
                packet,
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

    const flags = view.getUint8(3);
    const hasSender = (flags & FLAG_HAS_SENDER) !== 0;
    const payloadOffset = hasSender ? HEADER_SIZE + SENDER_UUID_SIZE : HEADER_SIZE;
    const payloadLength = view.getUint32(16, true);
    if (payloadLength > view.byteLength - payloadOffset) {
        return { malformed: true, reason: "payload length exceeds frame size" };
    }

    let senderUuid = null;
    if (hasSender) {
        if (view.byteLength < HEADER_SIZE + SENDER_UUID_SIZE) {
            return { malformed: true, reason: "missing sender uuid" };
        }
        senderUuid = uuidFromBytes(view, HEADER_SIZE);
    }

    const payload = new Uint8Array(arrayBuffer, payloadOffset, payloadLength);
    const panRaw = view.getInt16(8, true);
    const gainRaw = view.getInt16(10, true);
    const sampleRate = view.getUint16(12, true);
    const channels = Math.max(1, view.getUint8(14));

    return {
        malformed: false,
        flags,
        sequence: view.getUint32(4, true),
        pan: Math.max(-1, Math.min(1, panRaw / 32767)),
        gain: Math.max(0, Math.min(1, gainRaw / 32767)),
        sampleRate: sampleRate > 0 ? sampleRate : 48000,
        channels,
        codec: view.getUint8(15),
        senderUuid,
        payload
    };
}

/** @param {DataView} view @param {number} offset */
function uuidFromBytes(view, offset) {
    const hex = [];
    for (let i = 0; i < SENDER_UUID_SIZE; i++) {
        hex.push(view.getUint8(offset + i).toString(16).padStart(2, "0"));
    }
    return (
        `${hex.slice(0, 4).join("")}-` +
        `${hex.slice(4, 6).join("")}-` +
        `${hex.slice(6, 8).join("")}-` +
        `${hex.slice(8, 10).join("")}-` +
        `${hex.slice(10, 16).join("")}`
    );
}

/**
 * @param {Uint8Array} opusPayload
 * @param {string|null|undefined} senderUuid
 */
async function decodeOpus(opusPayload, senderUuid) {
    await warmupAudioDecompiler();
    if (!wasmReady || !OpusDecoderClass) {
        return null;
    }

    const key = senderUuid || "other";
    const decoder = await decoderForSender(key);
    if (!decoder) {
        return null;
    }

    const decoded = decoder.decodeFrame(opusPayload);
    const channelData = decoded?.channelData;
    if (!Array.isArray(channelData) || channelData.length === 0) {
        throw new Error("WASM decode returned empty channel data");
    }
    return channelData;
}

/** @param {string} key */
async function decoderForSender(key) {
    const now = Date.now();
    const existing = decodersBySender.get(key);
    if (existing) {
        existing.lastUsed = now;
        return existing.decoder;
    }

    const decoder = new OpusDecoderClass({
        sampleRate: 48000,
        channels: 1
    });
    await decoder.ready;
    decodersBySender.set(key, { decoder, lastUsed: now });
    pruneIdleDecoders(now);
    return decoder;
}

/** @param {number} now */
function pruneIdleDecoders(now) {
    if (decodersBySender.size <= 4) {
        return;
    }
    for (const [key, entry] of decodersBySender) {
        if (now - entry.lastUsed < DECODER_IDLE_MS) {
            continue;
        }
        // Keep at least one fallback decoder around.
        if (key === "other" && decodersBySender.size <= 5) {
            continue;
        }
        try {
            entry.decoder?.free?.();
        } catch {
            // ignore
        }
        decodersBySender.delete(key);
    }
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
