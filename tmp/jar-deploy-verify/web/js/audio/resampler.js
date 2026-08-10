/**
 * Stateful streaming resampler that preserves fractional phase across callbacks.
 * Uses 4-point Hermite interpolation when enough history exists, else linear.
 */

/**
 * @param {number} t fractional position in [0, 1)
 * @param {number} y0
 * @param {number} y1
 * @param {number} y2
 * @param {number} y3
 * @returns {number}
 */
export function hermiteInterpolate(t, y0, y1, y2, y3) {
    const c0 = y1;
    const c1 = 0.5 * (y2 - y0);
    const c2 = y0 - 2.5 * y1 + 2 * y2 - 0.5 * y3;
    const c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2);
    return ((c3 * t + c2) * t + c1) * t + c0;
}

/**
 * Linear interpolate between two samples.
 * @param {number} t
 * @param {number} y0
 * @param {number} y1
 * @returns {number}
 */
export function linearInterpolate(t, y0, y1) {
    return y0 + t * (y1 - y0);
}

/**
 * Streaming sample-rate converter. Do not construct a fresh resampler per callback —
 * fractional phase must carry across blocks to avoid drift and discontinuities.
 */
export class StreamingResampler {
    /**
     * @param {number} inputRate source sample rate (Hz)
     * @param {number} outputRate destination sample rate (Hz)
     */
    constructor(inputRate, outputRate) {
        if (!Number.isFinite(inputRate) || inputRate <= 0) {
            throw new Error(`Invalid inputRate: ${inputRate}`);
        }
        if (!Number.isFinite(outputRate) || outputRate <= 0) {
            throw new Error(`Invalid outputRate: ${outputRate}`);
        }

        this.inputRate = inputRate;
        this.outputRate = outputRate;
        /** Input samples advanced per output sample. */
        this.ratio = inputRate / outputRate;

        /** Fractional read position relative to {@link consumed}. */
        this.position = 0;

        /** @type {Float32Array} */
        this.buffer = new Float32Array(0);

        /** Whole-sample cursor into {@link buffer}. */
        this.consumed = 0;
    }

    /**
     * @returns {boolean}
     */
    needsResample() {
        return Math.abs(this.inputRate - this.outputRate) > 0.01;
    }

    /**
     * Push a block of mono float samples and return resampled mono floats.
     * @param {Float32Array} input
     * @returns {Float32Array}
     */
    process(input) {
        if (!input || input.length === 0) {
            return new Float32Array(0);
        }

        if (!this.needsResample()) {
            // Always copy on the identity path so callers never alias a worklet-
            // transferred or recycled buffer across callbacks.
            return input instanceof Float32Array ? new Float32Array(input) : new Float32Array(input);
        }

        this.#append(input);

        // Need buffer[i1] and buffer[i1+1] available for each output.
        const availableAhead = this.buffer.length - this.consumed - this.position - 1;
        const outEstimate = Math.max(0, Math.floor(availableAhead / this.ratio) + 1);
        if (outEstimate <= 0) {
            return new Float32Array(0);
        }

        const out = new Float32Array(outEstimate);
        let written = 0;

        while (written < out.length) {
            const absPos = this.consumed + this.position;
            const i1 = Math.floor(absPos);
            const frac = absPos - i1;
            const i2 = i1 + 1;

            if (i2 >= this.buffer.length) {
                break;
            }

            const i0 = i1 - 1;
            const i3 = i1 + 2;

            if (i0 >= 0 && i3 < this.buffer.length) {
                out[written] = hermiteInterpolate(
                    frac,
                    this.buffer[i0],
                    this.buffer[i1],
                    this.buffer[i2],
                    this.buffer[i3]
                );
            } else {
                out[written] = linearInterpolate(frac, this.buffer[i1], this.buffer[i2]);
            }

            this.position += this.ratio;
            written++;
        }

        const whole = Math.floor(this.position);
        if (whole > 0) {
            this.consumed += whole;
            this.position -= whole;
        }

        this.#compact();
        return written === out.length ? out : out.subarray(0, written);
    }

    /**
     * Reset all streaming state.
     */
    reset() {
        this.position = 0;
        this.buffer = new Float32Array(0);
        this.consumed = 0;
    }

    /**
     * Append input while preserving one history sample for Hermite.
     * @param {Float32Array} input
     */
    #append(input) {
        const history = Math.min(1, this.consumed);
        const start = this.consumed - history;
        const pending = this.buffer.length - start;
        const next = new Float32Array(pending + input.length);
        if (pending > 0) {
            next.set(this.buffer.subarray(start));
        }
        next.set(input, pending);
        this.buffer = next;
        this.consumed = history;
    }

    #compact() {
        const keepHistory = 1;
        if (this.consumed <= keepHistory) {
            return;
        }
        const drop = this.consumed - keepHistory;
        // Copy into a fresh buffer — never keep a growing subarray view chain.
        // Subarray views of recycled/transferred parents have produced torn reads.
        const keep = this.buffer.subarray(drop);
        this.buffer = new Float32Array(keep);
        this.consumed = keepHistory;
    }
}
