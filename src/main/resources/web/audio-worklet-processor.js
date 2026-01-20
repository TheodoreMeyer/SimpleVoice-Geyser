class AudioPlayerProcessor extends AudioWorkletProcessor {
    constructor() {
        super();
        this.queue = [];
        this.port.onmessage = (event) => {
            if (event.data.type === 'pcm') {
                this.queue.push(event.data.buffer);
            }
        };
    }

    process(inputs, outputs, parameters) {
        const output = outputs[0];
        const channel = output[0];

        if (this.queue.length === 0) {
            channel.fill(0); // silence if nothing to play
            return true;
        }

        const buffer = this.queue.shift();
        for (let i = 0; i < channel.length; i++) {
            channel[i] = buffer[i] || 0;
        }

        return true; // keep processor alive
    }
}

registerProcessor('audio-player-processor', AudioPlayerProcessor);
