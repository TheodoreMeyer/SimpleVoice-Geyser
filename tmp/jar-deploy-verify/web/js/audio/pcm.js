/**
 * PCM helpers for browser microphone outbound framing.
 * Float32 ↔ PCM16LE and multichannel → mono downmix.
 */

export const TARGET_SAMPLE_RATE = 48000;
export const FRAME_SAMPLES = 960;
export const FRAME_BYTES = FRAME_SAMPLES * 2;

/**
 * Clamp a float sample to [-1, 1].
 * @param {number} sample
 * @returns {number}
 */
export function clampFloat(sample) {
    if (sample > 1) return 1;
    if (sample < -1) return -1;
    return sample || 0;
}

/**
 * Convert a single Float32 sample to signed PCM16 using correct scaling.
 * Positive peak → 32767, negative peak → -32768.
 * @param {number} sample
 * @returns {number}
 */
export function floatToPcm16Sample(sample) {
    const s = clampFloat(sample);
    return s < 0 ? (s * 0x8000) | 0 : (s * 0x7fff) | 0;
}

/**
 * Convert a Float32Array (mono) to a little-endian PCM16 ArrayBuffer.
 * @param {Float32Array} samples
 * @returns {ArrayBuffer}
 */
export function floatToPcm16Le(samples) {
    const buffer = new ArrayBuffer(samples.length * 2);
    const view = new DataView(buffer);
    for (let i = 0; i < samples.length; i++) {
        view.setInt16(i * 2, floatToPcm16Sample(samples[i]), true);
    }
    return buffer;
}

/**
 * Downmix interleaved or planar multichannel Float32 to mono by averaging channels.
 * Never treats interleaved stereo as consecutive mono frames.
 *
 * @param {Float32Array|Float32Array[]} input planar channels OR single interleaved buffer
 * @param {number} [channels] required when input is interleaved Float32Array
 * @returns {Float32Array} mono samples
 */
export function downmixToMono(input, channels) {
    if (Array.isArray(input)) {
        const channelArrays = input.filter((c) => c instanceof Float32Array);
        if (channelArrays.length === 0) {
            return new Float32Array(0);
        }
        if (channelArrays.length === 1) {
            return channelArrays[0];
        }
        const length = channelArrays[0].length;
        const out = new Float32Array(length);
        const n = channelArrays.length;
        for (let i = 0; i < length; i++) {
            let sum = 0;
            for (let c = 0; c < n; c++) {
                sum += channelArrays[c][i] || 0;
            }
            out[i] = sum / n;
        }
        return out;
    }

    if (!(input instanceof Float32Array)) {
        return new Float32Array(0);
    }

    const ch = Number.isFinite(channels) && channels > 0 ? channels | 0 : 1;
    if (ch === 1) {
        return input;
    }

    const frames = Math.floor(input.length / ch);
    const out = new Float32Array(frames);
    for (let i = 0; i < frames; i++) {
        let sum = 0;
        const base = i * ch;
        for (let c = 0; c < ch; c++) {
            sum += input[base + c] || 0;
        }
        out[i] = sum / ch;
    }
    return out;
}

/**
 * Accumulate mono float samples and emit complete frames of {@link FRAME_SAMPLES}.
 * Remainder is kept in the accumulator for the next call.
 */
export class FrameAccumulator {
    /**
     * @param {number} [frameSize=FRAME_SAMPLES]
     */
    constructor(frameSize = FRAME_SAMPLES) {
        this.frameSize = frameSize;
        /** @type {Float32Array} */
        this.remainder = new Float32Array(0);
    }

    /**
     * Push mono samples; returns zero or more complete frames (each Float32Array of frameSize).
     * @param {Float32Array} samples
     * @returns {Float32Array[]}
     */
    push(samples) {
        if (!samples || samples.length === 0) {
            return [];
        }

        const total = this.remainder.length + samples.length;
        const merged = new Float32Array(total);
        merged.set(this.remainder, 0);
        merged.set(samples, this.remainder.length);

        const frames = [];
        let offset = 0;
        while (offset + this.frameSize <= merged.length) {
            frames.push(merged.subarray(offset, offset + this.frameSize).slice());
            offset += this.frameSize;
        }

        this.remainder = merged.subarray(offset).slice();
        return frames;
    }

    /**
     * Clear remainder state.
     */
    reset() {
        this.remainder = new Float32Array(0);
    }

    /**
     * @returns {number}
     */
    pendingSamples() {
        return this.remainder.length;
    }
}
