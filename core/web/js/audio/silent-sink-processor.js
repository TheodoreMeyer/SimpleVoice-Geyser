class SilentSinkCounter extends AudioWorkletProcessor {
    constructor() {
        super();
        this._calls = 0;
        this._inputSamples = 0;
        this._frames960 = 0;
        this._accum960 = 0;
        this.port.onmessage = (event) => {
            if (event.data?.type === "flush") {
                this.#postStats(128);
            }
        };
    }

    #postStats(quantumSize) {
        this.port.postMessage({
            type: "tick",
            workletCalls: this._calls,
            inputSamples: this._inputSamples,
            frames960: this._frames960,
            sampleRate,
            quantumSize
        });
    }

    process(inputs) {
        const ch = inputs[0];
        if (!ch || !ch[0]) {
            return true;
        }
        const frameLength = ch[0].length;
        this._calls++;
        this._inputSamples += frameLength;
        this._accum960 += frameLength;
        this._frames960 += Math.floor(this._accum960 / 960);
        this._accum960 %= 960;

        if ((this._calls % 128) === 0) {
            this.#postStats(frameLength);
        }
        return true;
    }
}

registerProcessor("silent-sink-counter", SilentSinkCounter);
