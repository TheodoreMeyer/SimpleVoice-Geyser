export const PCM_MIC_FORMAT = "pcm16le";
export const OPUS_MIC_FORMAT = "opus";
export const MIC_OPUS_ENCODER_CONFIG = Object.freeze({
    codec: "opus",
    sampleRate: 48000,
    numberOfChannels: 1,
    bitrate: 32000,
    opus: Object.freeze({format: "opus", frameDuration: 20000})
});

export function micFormatFromAck(format) {
    return format === OPUS_MIC_FORMAT ? OPUS_MIC_FORMAT : PCM_MIC_FORMAT;
}

export function micFormatsForEncoder(sampleRate, opusEncoderSupported) {
    return sampleRate === 48000 && opusEncoderSupported
        ? [OPUS_MIC_FORMAT, PCM_MIC_FORMAT]
        : [PCM_MIC_FORMAT];
}

export function isMicFrameFresh(captureTimestamp, currentTimestamp, maxAgeMs) {
    const ageMs = (currentTimestamp - captureTimestamp) / 1000;
    return ageMs >= 0 && ageMs <= maxAgeMs;
}

export function isSupportedOpusChunk(chunk) {
    return Number.isInteger(chunk?.byteLength)
        && chunk.byteLength > 0
        && chunk.byteLength <= 1275
        && (chunk.duration == null || chunk.duration === 20000);
}

export function isBackendTransferStatus(message) {
    const normalized = String(message || "").toLowerCase().replace(/\s/g, "");
    return normalized.includes("changingserver");
}
