class AudioPlayerProcessor extends AudioWorkletProcessor {
    constructor() {
        super();

        this.buffer = new Float32Array(48000); // 1 second ring buffer
        this.writeIndex = 0;
        this.readIndex = 0;
        this.available = 0;

        this.started = false;

        //for testing/diagnostics
        this.underruns = 0;
        this._lastStatsTime = 0;

        this.MAX_BUFFER = this.buffer.length;
        this.TARGET_BUFFER = 960 * 7; // start playback when >= this
        this.MIN_BUFFER = 960  * 3; // never drop below this while reading

        this.lastSample = 0; // for repeating when underrun

        //For resetting state
        this.silenceFrames = 0;
        this.SILENCE_RESET_FRAMES = 128 * 15;

        this.port.onmessage = (event) => {
            if (event.data.type === 'pcm') {
                const input = event.data.buffer;

                for (let i = 0; i < input.length; i++) {
                    // Drop the oldest audio if buffer is full
                    if (this.available >= this.MAX_BUFFER) {
                        this.readIndex = (this.readIndex + 1) % this.MAX_BUFFER;
                        this.available--;
                    }

                    this.buffer[this.writeIndex] = input[i];
                    this.writeIndex = (this.writeIndex + 1) % this.MAX_BUFFER;
                    this.available++;
                }
            } else if (event.data.type === 'reset') {
                // Hard reset to clean state
                this.writeIndex = 0;
                this.readIndex = 0;
                this.available = 0;

                this.started = false;
                this.lastSample = 0;
                this.silenceFrames = 0;
                this.underruns = 0;

                this.buffer.fill(0);
            }

        };
    }

    process(inputs, outputs) {
        const output = outputs[0][0];
        const framesNeeded = output.length;

        // --- detect prolonged silence (buffer fully drained) ---
        if (this.available === 0) {
            this.silenceFrames += framesNeeded;

            if (this.silenceFrames >= this.SILENCE_RESET_FRAMES) {
                // Treat this like end-of-utterance
                this.started = false;
                this.lastSample = 0;
            }

            output.fill(0);
            return true;
        } else {
            // audio resumed, clear silence counter
            this.silenceFrames = 0;
        }

        // --- don't start until target buffer is filled ---
        if (!this.started) {
            if (this.available < this.TARGET_BUFFER) {
                output.fill(0);
                return true;
            }
            this.started = true;
        }

        let i = 0;

        // --- play available buffered audio ---
        for (; i < framesNeeded; i++) {
            if (this.available > this.MIN_BUFFER) {
                const sample = this.buffer[this.readIndex];
                output[i] = sample;
                this.lastSample = sample;

                this.readIndex = (this.readIndex + 1) % this.MAX_BUFFER;
                this.available--;
            } else {
                // Simple exponential decay PLC
                this.lastSample *= 0.995;
                const noise = (Math.random() * 2 - 1) * 0.00002;
                output[i] = this.lastSample + noise;
                this.underruns++;
            }
        }

        // --- diagnostics logging every 500ms ---
        const now = this.currentTime;
        if (now - this._lastStatsTime > 0.5) {
            this.port.postMessage({
                type: 'stats',
                buffered: this.available,
                underruns: this.underruns
            });
            this._lastStatsTime = now;
        }

        return true;
    }
}

registerProcessor('pcm-player', AudioPlayerProcessor);
