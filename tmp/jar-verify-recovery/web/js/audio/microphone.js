class Microphone extends AudioWorkletProcessor {
    constructor() {
        super();
        // ~400 ms hangover at 48 kHz / 128-sample quanta ≈ 150 frames.
        // Recalculated per process() from sampleRate and quantum length.
        this._hangover = 0;
        this._bypassVad = false;
        this._sourceSamplesProcessed = 0;
        this._outputSamplesProduced = 0;
        this._framesProduced = 0;
        this._quantumSequence = 0;
        this._metricsEvery = 375; // ~1s at 128-sample / 48 kHz
        this.port.onmessage = (event) => {
            const data = event.data || {};
            if (data.type === "setVadBypass") {
                this._bypassVad = !!data.enabled;
                if (this._bypassVad) {
                    this._hangover = 0;
                }
            }
        };
    }

    process(inputs, outputs) {
        const channels = inputs[0];
        // Explicitly silence outputs — never pass input through (would risk
        // monitor loops and confuse graph keep-alive accounting).
        if (outputs && outputs[0]) {
            for (let c = 0; c < outputs[0].length; c++) {
                if (outputs[0][c]) {
                    outputs[0][c].fill(0);
                }
            }
        }
        if (!channels || channels.length === 0 || !channels[0]) {
            return true;
        }

        const frameLength = channels[0].length;
        const channelCount = channels.length;
        if (frameLength <= 0) {
            return true;
        }

        // Average all channels → mono (never treat interleaved stereo as consecutive mono).
        // Always allocate a fresh buffer; channel views are reused by the audio engine.
        const mono = new Float32Array(frameLength);
        for (let i = 0; i < frameLength; i++) {
            let sum = 0;
            for (let c = 0; c < channelCount; c++) {
                sum += channels[c][i] || 0;
            }
            mono[i] = sum / channelCount;
        }

        this._sourceSamplesProcessed += frameLength;
        this._outputSamplesProduced += frameLength;
        this._framesProduced++;
        this._quantumSequence++;

        let energySum = 0;
        for (let i = 0; i < mono.length; i++) {
            const s = mono[i];
            energySum += s * s;
        }
        const energy = energySum / mono.length;

        const START_THRESHOLD = 0.00008;
        const STOP_THRESHOLD = 0.00004;
        // 400 ms hangover bridges normal syllable/word gaps without chopping speech.
        const hangoverMs = 400;
        const quantumMs = (frameLength / (sampleRate || 48000)) * 1000;
        const hangoverFrames = Math.max(1, Math.ceil(hangoverMs / Math.max(0.1, quantumMs)));

        let isSpeech;
        if (this._bypassVad) {
            isSpeech = true;
            this._hangover = hangoverFrames;
        } else {
            if (energy > START_THRESHOLD) {
                this._hangover = hangoverFrames;
            } else if (energy < STOP_THRESHOLD && this._hangover > 0) {
                this._hangover--;
            }
            isSpeech = this._hangover > 0;
        }

        const includeMetrics = (this._quantumSequence % this._metricsEvery) === 0;
        const message = {
            samples: mono,
            speech: isSpeech,
            channels: channelCount,
            sampleRate: sampleRate,
            energy: energy,
            quantumSequence: this._quantumSequence
        };
        if (includeMetrics) {
            message.sourceSamplesProcessed = this._sourceSamplesProcessed;
            message.outputSamplesProduced = this._outputSamplesProduced;
            message.framesProduced = this._framesProduced;
        }

        // Transfer the PCM buffer so the main thread cannot observe a recycled view.
        this.port.postMessage(message, [mono.buffer]);

        return true;
    }
}

registerProcessor("mic-capture", Microphone);
