class Microphone extends AudioWorkletProcessor {
    constructor() {
        super();

        this._hangover = 0;
        this._frame = new Float32Array(960);
        this._frameOffset = 0;
        this._frameSpeech = false;
        this._frameStart = 0;

        // Defaults match previous hardcoded VAD thresholds
        this._startThreshold = 0.00008;
        this._stopThreshold = 0.00004;
        // Amplitude boost is applied by an AudioContext GainNode before this worklet.
        // Keep a residual multiplier (default 1) for compatibility / soft-limit only.
        this._inputGain = 1.0;

        this.port.onmessage = (e) => {
            const data = e.data;
            if (!data || typeof data !== "object") return;
            if (data.type === "setThreshold") {
                if (Number.isFinite(data.start)) this._startThreshold = data.start;
                if (Number.isFinite(data.stop)) this._stopThreshold = data.stop;
            } else if (data.type === "setGain") {
                if (Number.isFinite(data.gain)) {
                    this._inputGain = Math.max(0.1, Math.min(8, data.gain));
                }
            }
        };
    }

    process(inputs) {
        const input = inputs[0]?.[0];
        if (!input) return true;

        const gain = this._inputGain;
        const scratch = new Float32Array(input.length);
        for (let i = 0; i < input.length; i++) {
            const s = input[i] * gain;
            // Soft limiter (tanh): quieter speech can be boosted hard without harsh hard-clip.
            scratch[i] = Math.tanh(s);
        }

        // ---- 1) Compute energy (RMS²) on gained samples ----
        let sum = 0;
        for (let i = 0; i < scratch.length; i++) {
            const s = scratch[i];
            sum += s * s;
        }

        const energy = sum / scratch.length;

        // ---- 2) VAD thresholds (hysteresis) ----
        const HANGOVER_FRAMES = 18; // ~31.5 ms @ 128 frames

        // ---- 3) Hangover-based VAD ----
        if (energy > this._startThreshold) {
            this._hangover = HANGOVER_FRAMES;
        } else if (energy < this._stopThreshold && this._hangover > 0) {
            this._hangover--;
        }

        this._frameSpeech ||= this._hangover > 0;
        for (let i = 0; i < scratch.length; i++) {
            if (this._frameOffset === 0) this._frameStart = currentFrame + i;
            this._frame[this._frameOffset++] = scratch[i];
            if (this._frameOffset === this._frame.length) {
                const samples = this._frame;
                this._frame = new Float32Array(960);
                this._frameOffset = 0;
                const timestamp = Math.round(this._frameStart * 1000000 / sampleRate);
                this.port.postMessage(
                    {samples, speech: this._frameSpeech, timestamp, energy},
                    [samples.buffer]
                );
                this._frameSpeech = false;
            }
        }

        return true;
    }
}

registerProcessor('mic-capture', Microphone);
