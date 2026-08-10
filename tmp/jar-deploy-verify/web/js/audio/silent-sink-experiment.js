/**
 * Minimal AudioWorklet silent-sink experiment.
 * Compares worklet-only vs worklet → Gain(0) → destination quantum rates.
 */

const PROCESSOR_URL = new URL("./silent-sink-processor.js", import.meta.url).href;

/** @type {WeakSet<AudioContext>} */
const loadedContexts = new WeakSet();

async function ensureProcessor(ctx) {
    if (loadedContexts.has(ctx)) return;
    await ctx.audioWorklet.addModule(PROCESSOR_URL);
    loadedContexts.add(ctx);
}

/**
 * @param {"worklet-only"|"silent-sink"} mode
 * @param {number} durationSec
 * @returns {Promise<{ mode: string, durationSec: number, sampleRate: number, quantumSize: number, sourceSamples: number, workletCalls: number, frames960: number }>}
 */
export async function runSilentSinkTrial(mode, durationSec = 10) {
    const AudioContextCtor = window.AudioContext || window.webkitAudioContext;
    if (!AudioContextCtor) {
        throw new Error("AudioContext unavailable");
    }

    const ctx = new AudioContextCtor({ sampleRate: 48000 });
    await ensureProcessor(ctx);
    await ctx.resume();

    const worklet = new AudioWorkletNode(ctx, "silent-sink-counter", {
        numberOfInputs: 1,
        numberOfOutputs: 1,
        outputChannelCount: [1],
        channelCount: 1
    });

    const osc = ctx.createOscillator();
    osc.frequency.value = 440;
    osc.type = "sine";
    osc.connect(worklet);

    let silentGain = null;
    if (mode === "silent-sink") {
        silentGain = ctx.createGain();
        silentGain.gain.value = 0;
        worklet.connect(silentGain);
        silentGain.connect(ctx.destination);
    }

    /** @type {ReturnType<typeof setInterval>|null} */
    let sourceSampleTimer = null;
    let sourceSamples = 0;
    const sampleRate = ctx.sampleRate;
    sourceSampleTimer = setInterval(() => {
        sourceSamples += Math.round(sampleRate * 0.05);
    }, 50);

    let lastTick = {
        workletCalls: 0,
        inputSamples: 0,
        frames960: 0,
        quantumSize: 128
    };

    worklet.port.onmessage = (event) => {
        if (event.data?.type === "tick") {
            lastTick = event.data;
        }
    };

    osc.start();

    await new Promise((resolve) => setTimeout(resolve, durationSec * 1000));

    worklet.port.postMessage({ type: "flush" });
    await new Promise((resolve) => setTimeout(resolve, 100));

    osc.stop();
    clearInterval(sourceSampleTimer);
    sourceSamples = Math.round(durationSec * sampleRate);

    worklet.port.onmessage = null;
    try {
        osc.disconnect();
        worklet.disconnect();
        silentGain?.disconnect();
    } catch {
        // ignore
    }

    await ctx.close();

    return {
        mode,
        durationSec,
        sampleRate,
        quantumSize: lastTick.quantumSize || 128,
        sourceSamples,
        workletCalls: lastTick.workletCalls,
        inputSamples: lastTick.inputSamples,
        frames960: lastTick.frames960
    };
}

if (typeof window !== "undefined") {
    window.runSilentSinkTrial = runSilentSinkTrial;
}
