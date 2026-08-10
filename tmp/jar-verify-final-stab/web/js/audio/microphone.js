class Microphone extends AudioWorkletProcessor {
    constructor() {
        super();
        // ~400 ms hangover at 48 kHz / 128-sample quanta ≈ 150 frames.
        // Recalculated per process() from sampleRate and quantum length.
        this._hangover = 0;
        this._bypassVad = false;
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

    process(inputs) {
        const channels = inputs[0];
        if (!channels || channels.length === 0 || !channels[0]) {
            return true;
        }

        const frameLength = channels[0].length;
        const channelCount = channels.length;

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

        // Transfer the PCM buffer so the main thread cannot observe a recycled view.
        this.port.postMessage(
            {
                samples: mono,
                speech: isSpeech,
                channels: channelCount,
                sampleRate: sampleRate,
                energy: energy
            },
            [mono.buffer]
        );

        return true;
    }
}

registerProcessor("mic-capture", Microphone);
