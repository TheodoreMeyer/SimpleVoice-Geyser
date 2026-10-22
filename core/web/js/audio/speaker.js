/**
 * Per-voice ring buffers mixed in process() so concurrent talkers sum instead of append.
 */
class SpeakerProcessor extends AudioWorkletProcessor {
    constructor() {
        super();

        this.voices = new Map();
        this.started = false;

        this.underruns = 0;
        this._lastStatsTime = 0;
        this.droppedFrames = 0;

        this.MAX_BUFFER = 48000; // 1 second at 48kHz per voice
        this.TARGET_BUFFER = 960 * 7;
        this.VOICE_IDLE_FRAMES = 128 * 30; // ~80ms * 30 ≈ drop idle voice

        this.lastSampleLeft = 0;
        this.lastSampleRight = 0;
        this.silenceFrames = 0;
        this.SILENCE_RESET_FRAMES = 128 * 15;

        this.port.onmessage = (event) => {
            if (event.data.type === "pcm") {
                const packet = event.data.buffer;
                if (!packet) {
                    return;
                }

                if (packet instanceof Float32Array) {
                    this.enqueueMono("legacy", packet);
                    return;
                }

                const input = packet.samples;
                const channels = packet.channels === 2 ? 2 : 1;
                const senderId = String(packet.senderId || "legacy");
                if (!(input instanceof Float32Array)) {
                    return;
                }

                if (channels === 2) {
                    this.enqueueStereoInterleaved(senderId, input);
                } else {
                    this.enqueueMono(senderId, input);
                }
            } else if (event.data.type === "reset") {
                this.voices.clear();
                this.started = false;
                this.lastSampleLeft = 0;
                this.lastSampleRight = 0;
                this.silenceFrames = 0;
                this.underruns = 0;
                this.droppedFrames = 0;
            }
        };
    }

    voiceFor(senderId) {
        let voice = this.voices.get(senderId);
        if (!voice) {
            voice = {
                left: new Float32Array(this.MAX_BUFFER),
                right: new Float32Array(this.MAX_BUFFER),
                writeIndex: 0,
                readIndex: 0,
                available: 0,
                idleFrames: 0
            };
            this.voices.set(senderId, voice);
        }
        return voice;
    }

    enqueueFrame(voice, left, right) {
        if (voice.available >= this.MAX_BUFFER) {
            voice.readIndex = (voice.readIndex + 1) % this.MAX_BUFFER;
            voice.available--;
            this.droppedFrames++;
        }

        voice.left[voice.writeIndex] = left;
        voice.right[voice.writeIndex] = right;
        voice.writeIndex = (voice.writeIndex + 1) % this.MAX_BUFFER;
        voice.available++;
        voice.idleFrames = 0;
    }

    enqueueMono(senderId, input) {
        const voice = this.voiceFor(senderId);
        for (let i = 0; i < input.length; i++) {
            const sample = input[i];
            this.enqueueFrame(voice, sample, sample);
        }
    }

    enqueueStereoInterleaved(senderId, input) {
        const voice = this.voiceFor(senderId);
        const frameCount = Math.floor(input.length / 2);
        for (let i = 0; i < frameCount; i++) {
            this.enqueueFrame(voice, input[i * 2], input[i * 2 + 1]);
        }
    }

    maxAvailable() {
        let max = 0;
        for (const voice of this.voices.values()) {
            if (voice.available > max) {
                max = voice.available;
            }
        }
        return max;
    }

    process(inputs, outputs) {
        const outputLeft = outputs[0][0];
        const outputRight = outputs[0][1] || null;
        const framesNeeded = outputLeft.length;
        const buffered = this.maxAvailable();

        if (buffered === 0) {
            this.silenceFrames += framesNeeded;
            if (this.silenceFrames >= this.SILENCE_RESET_FRAMES) {
                this.started = false;
                this.lastSampleLeft = 0;
                this.lastSampleRight = 0;
            }
            outputLeft.fill(0);
            if (outputRight) {
                outputRight.fill(0);
            }
            this.pruneIdleVoices(framesNeeded);
            return true;
        }

        this.silenceFrames = 0;

        if (!this.started) {
            if (buffered < this.TARGET_BUFFER) {
                outputLeft.fill(0);
                if (outputRight) {
                    outputRight.fill(0);
                }
                return true;
            }
            this.started = true;
        }

        for (let i = 0; i < framesNeeded; i++) {
            let leftSum = 0;
            let rightSum = 0;
            let contributed = 0;

            for (const voice of this.voices.values()) {
                if (voice.available > 0) {
                    leftSum += voice.left[voice.readIndex];
                    rightSum += voice.right[voice.readIndex];
                    voice.readIndex = (voice.readIndex + 1) % this.MAX_BUFFER;
                    voice.available--;
                    voice.idleFrames = 0;
                    contributed++;
                } else {
                    voice.idleFrames++;
                }
            }

            let left;
            let right;
            if (contributed > 0) {
                // Soft-limit mix peaks when several people talk.
                left = Math.tanh(leftSum);
                right = Math.tanh(rightSum);
                this.lastSampleLeft = left;
                this.lastSampleRight = right;
            } else {
                this.lastSampleLeft *= 0.995;
                this.lastSampleRight *= 0.995;
                const noise = (Math.random() * 2 - 1) * 0.00002;
                left = this.lastSampleLeft + noise;
                right = this.lastSampleRight + noise;
                this.underruns++;
            }

            outputLeft[i] = left;
            if (outputRight) {
                outputRight[i] = right;
            }
        }

        this.pruneIdleVoices(0);

        const now = this.currentTime;
        if (now - this._lastStatsTime > 0.5) {
            this.port.postMessage({
                type: "stats",
                buffered: this.maxAvailable(),
                underruns: this.underruns,
                voices: this.voices.size
            });
            this._lastStatsTime = now;
        }

        return true;
    }

    pruneIdleVoices(extraIdle) {
        if (this.voices.size === 0) {
            return;
        }
        for (const [id, voice] of this.voices) {
            if (extraIdle > 0 && voice.available === 0) {
                voice.idleFrames += extraIdle;
            }
            if (voice.available === 0 && voice.idleFrames >= this.VOICE_IDLE_FRAMES) {
                this.voices.delete(id);
            }
        }
    }
}

registerProcessor("pcm-player", SpeakerProcessor);
