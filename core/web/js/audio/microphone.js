class Microphone extends AudioWorkletProcessor {
    constructor() {
        super();
        this._hangover = 0;
        this._startThreshold = 0.00008;
        this._stopThreshold  = 0.00004;

        this.port.onmessage = (e) => {
            if (e.data.type === 'setThreshold') {
                this._startThreshold = e.data.start;
                this._stopThreshold  = e.data.stop;
            }
        };
    }

    process(inputs) {
        const input = inputs[0]?.[0];
        if (!input) return true;

        let sum = 0;
        for (let i = 0; i < input.length; i++) {
            const s = input[i];
            sum += s * s;
        }
        const energy = sum / input.length;
        const HANGOVER_FRAMES = 18;

        if (energy > this._startThreshold) {
            this._hangover = HANGOVER_FRAMES;
        } else if (energy < this._stopThreshold && this._hangover > 0) {
            this._hangover--;
        }

        const isSpeech = this._hangover > 0;

        this.port.postMessage({
            samples: new Float32Array(input),
            speech: isSpeech,
            energy: energy
        });

        return true;
    }
}

registerProcessor('mic-capture', Microphone);
