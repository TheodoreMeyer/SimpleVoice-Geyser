class MicCaptureProcessor extends AudioWorkletProcessor {
    constructor() {
        super();

        // Hangover counter (measured in render quanta)
        this._hangover = 0;
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
        const START_THRESHOLD = 0.00012; // speech starts
        const STOP_THRESHOLD  = 0.00008; // speech ends, about START_THRESHOLD × 0.6 – 0.75
        const HANGOVER_FRAMES = 12;       // ~31.5 ms @ 128 frames

        // ---- 3) Hangover-based VAD ----
        if (energy > START_THRESHOLD) {
            // Strong speech → refresh hangover
            this._hangover = HANGOVER_FRAMES;
        } else if (energy < STOP_THRESHOLD && this._hangover > 0) {
            // Quiet AND hangover active → decay
            this._hangover--;
        }

        const isSpeech = this._hangover > 0;

        // ---- 4) Send data to main thread ----
        this.port.postMessage({
            samples: input,
            speech: isSpeech
        });

        return true;
    }
}

registerProcessor('mic-capture', MicCaptureProcessor);
