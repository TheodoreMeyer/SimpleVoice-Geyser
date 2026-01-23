class AudioPlayerProcessor extends AudioWorkletProcessor {
    constructor() {
        super();

        this.buffer = new Float32Array(48000); // 1 second ring buffer
        this.writeIndex = 0;
        this.readIndex = 0;
        this.available = 0;

        this.MAX_BUFFER = this.buffer.length;
        this.TARGET_BUFFER = 4800; // ~100ms latency

        this.port.onmessage = (event) => {
            if (event.data.type === 'pcm') {
                const input = event.data.buffer;

                this.port.postMessage({ type: 'log', message: `Playback readIndex=${this.readIndex}, available=${this.available}` });

                for (let i = 0; i < input.length; i++) {
                    // Drop oldest audio if buffer is full
                    if (this.available >= this.MAX_BUFFER) {
                        this.readIndex = (this.readIndex + 1) % this.MAX_BUFFER;
                        this.available--;
                    }

                    this.buffer[this.writeIndex] = input[i];
                    this.writeIndex = (this.writeIndex + 1) % this.MAX_BUFFER;
                    this.available++;
                }
            }
        };
    }

    process(inputs, outputs) {
        const output = outputs[0][0];
        const framesNeeded = output.length; // usually 128

        // --- lightweight heartbeat log (rate-limited) ---
        if ((this._logCounter = (this._logCounter || 0) + 1) % 200 === 0) {
            this.port.postMessage({
                type: 'log',
                message:
                    `process() frames=${framesNeeded}, available=${this.available}`
            });
        }

        let i = 0;

        // --- play available buffered audio ---
        for (; i < framesNeeded && this.available > 0; i++) {
            output[i] = this.buffer[this.readIndex];
            this.readIndex = (this.readIndex + 1) % this.MAX_BUFFER;
            this.available--;
        }

        // --- pad with silence if underrun ---
        if (i < framesNeeded) {
            output.fill(0, i);
        }

        return true; // keep processor alive
    }
}

registerProcessor('pcm-player', AudioPlayerProcessor);