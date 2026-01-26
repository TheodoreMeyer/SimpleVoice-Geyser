class MicCaptureProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this._buffer = [];
    }

    process(inputs) {
        const input = inputs[0];
        if (!input || !input[0]) return true;

        const channel = input[0];

        // Accumulate samples until main thread decides what to do
        this.port.postMessage(channel);

        return true;
    }
}

registerProcessor('mic-capture', MicCaptureProcessor);
