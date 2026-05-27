class SpeakerProcessor extends AudioWorkletProcessor {
    constructor() {
        super();

        // Ring buffer stores frame-aligned stereo samples.
        this.leftBuffer = new Float32Array(48000); // 1 second at 48kHz
        this.rightBuffer = new Float32Array(48000);
        this.writeIndex = 0;
        this.readIndex = 0;
        this.available = 0;

        this.started = false;

        //for testing/diagnostics
        this.underruns = 0;
        this._lastStatsTime = 0;

        this.MAX_BUFFER = this.leftBuffer.length;
        this.TARGET_BUFFER = 960 * 7; // start playback when >= this
        this.MIN_BUFFER = 960  * 3; // diagnostic threshold only

        this.lastSampleLeft = 0;
        this.lastSampleRight = 0;

        this.droppedFrames = 0; //Dropped amount of frames

        //For resetting state
        this.silenceFrames = 0;
        this.SILENCE_RESET_FRAMES = 128 * 15;

        this.port.onmessage = (event) => {
            if (event.data.type === 'pcm') {
                const packet = event.data.buffer;
                if (!packet) {
                    return;
                }

                // Backward compatible path for direct Float32Array mono payloads.
                if (packet instanceof Float32Array) {
                    this.enqueueMono(packet);
                    return;
                }

                const input = packet.samples;
                const channels = packet.channels === 2 ? 2 : 1;
                if (!(input instanceof Float32Array)) {
                    return;
                }

                if (channels === 2) {
                    this.enqueueStereoInterleaved(input);
                } else {
                    this.enqueueMono(input);
                }
            } else if (event.data.type === 'reset') {
                this.writeIndex = 0;
                this.readIndex = 0;
                this.available = 0;

                this.started = false;
                this.lastSampleLeft = 0;
                this.lastSampleRight = 0;
                this.silenceFrames = 0;
                this.underruns = 0;
                this.droppedFrames = 0;

                this.leftBuffer.fill(0);
                this.rightBuffer.fill(0);
            }

        };
    }

    enqueueFrame(left, right) {
        if (this.available >= this.MAX_BUFFER) {
            this.readIndex = (this.readIndex + 1) % this.MAX_BUFFER;
            this.available--;
            this.droppedFrames++;
        }

        this.leftBuffer[this.writeIndex] = left;
        this.rightBuffer[this.writeIndex] = right;
        this.writeIndex = (this.writeIndex + 1) % this.MAX_BUFFER;
        this.available++;
    }

    enqueueMono(input) {
        for (let i = 0; i < input.length; i++) {
            const sample = input[i];
            this.enqueueFrame(sample, sample);
        }
    }

    enqueueStereoInterleaved(input) {
        const frameCount = Math.floor(input.length / 2);
        for (let i = 0; i < frameCount; i++) {
            this.enqueueFrame(input[i * 2], input[i * 2 + 1]);
        }
    }

    process(inputs, outputs) {
        const outputLeft = outputs[0][0];
        const outputRight = outputs[0][1] || null;
        const framesNeeded = outputLeft.length;

        // --- detect prolonged silence (buffer fully drained) ---
        if (this.available === 0) {
            this.silenceFrames += framesNeeded;

            if (this.silenceFrames >= this.SILENCE_RESET_FRAMES) {
                // Treat this like end-of-utterance
                this.started = false;
                this.lastSampleLeft = 0;
                this.lastSampleRight = 0;
            }

            outputLeft.fill(0);
            if (outputRight) {
                outputRight.fill(0);
            }
            return true;
        } else {
            // audio resumed, clear silence counter
            this.silenceFrames = 0;
        }

        // --- don't start until target buffer is filled ---
        if (!this.started) {
            if (this.available < this.TARGET_BUFFER) {
                outputLeft.fill(0);
                if (outputRight) {
                    outputRight.fill(0);
                }
                return true;
            }
            this.started = true;
        }

        let i = 0;

        // --- play available buffered audio ---
        for (; i < framesNeeded; i++) {
            if (this.available > 0) {
                const left = this.leftBuffer[this.readIndex];
                const right = this.rightBuffer[this.readIndex];
                outputLeft[i] = left;
                if (outputRight) {
                    outputRight[i] = right;
                }
                this.lastSampleLeft = left;
                this.lastSampleRight = right;

                this.readIndex = (this.readIndex + 1) % this.MAX_BUFFER;
                this.available--;
            } else {
                // True underrun only: apply lightweight PLC once buffer is empty.
                this.lastSampleLeft *= 0.995;
                this.lastSampleRight *= 0.995;
                const noise = (Math.random() * 2 - 1) * 0.00002;
                outputLeft[i] = this.lastSampleLeft + noise;
                if (outputRight) {
                    outputRight[i] = this.lastSampleRight + noise;
                }
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

registerProcessor('pcm-player', SpeakerProcessor);
