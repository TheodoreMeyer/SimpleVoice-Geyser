class Microphone extends AudioWorkletProcessor {
    constructor() {
        super();
        this._hangover = 0;
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
        const HANGOVER_FRAMES = 18;

        if (energy > START_THRESHOLD) {
            this._hangover = HANGOVER_FRAMES;
        } else if (energy < STOP_THRESHOLD && this._hangover > 0) {
            this._hangover--;
        }

        const isSpeech = this._hangover > 0;

        // Transfer the PCM buffer so the main thread cannot observe a recycled view.
        // Cloning without transfer has raced against render-quantum reuse in some engines,
        // which warps pitch/timbre (robotic) when the main thread reads stale/torn samples.
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
