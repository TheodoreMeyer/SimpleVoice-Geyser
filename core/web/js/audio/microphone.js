class Microphone extends AudioWorkletProcessor {
    constructor() {
        super();

        this._hangover = 0;
        this._frame = new Float32Array(960);
        this._frameOffset = 0;
        this._frameSpeech = false;
        this._frameStart = 0;
    }

    process(inputs) {
        const input = inputs[0]?.[0];
        if (!input) return true;

        // ---- 1) Compute energy (RMS²) ----
        let sum = 0;
        for (let i = 0; i < input.length; i++) {
            const s = input[i];
            sum += s * s;
        }

        const energy = sum / input.length;

        // ---- 2) VAD thresholds (hysteresis) ----
        const START_THRESHOLD = 0.00008; // speech starts
        const STOP_THRESHOLD  = 0.00004; // speech ends
        const HANGOVER_FRAMES = 18;       // ~31.5 ms @ 128 frames

        // ---- 3) Hangover-based VAD ----
        if (energy > START_THRESHOLD) {
            // Strong speech → refresh hangover
            this._hangover = HANGOVER_FRAMES;
        } else if (energy < STOP_THRESHOLD && this._hangover > 0) {
            // Quiet AND hangover active → decay
            this._hangover--;
        }

        this._frameSpeech ||= this._hangover > 0;
        for (let i = 0; i < input.length; i++) {
            if (this._frameOffset === 0) this._frameStart = currentFrame + i;
            this._frame[this._frameOffset++] = input[i];
            if (this._frameOffset === this._frame.length) {
                const samples = this._frame;
                this._frame = new Float32Array(960);
                this._frameOffset = 0;
                const timestamp = Math.round(this._frameStart * 1000000 / sampleRate);
                this.port.postMessage({samples, speech: this._frameSpeech, timestamp}, [samples.buffer]);
                this._frameSpeech = false;
            }
        }

        return true;
    }
}

registerProcessor('mic-capture', Microphone);
