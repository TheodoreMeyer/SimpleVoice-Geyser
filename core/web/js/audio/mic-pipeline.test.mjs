/**
 * Extended microphone pipeline contract tests.
 * Run: node --test core/web/js/audio/pcm.test.mjs core/web/js/audio/resampler.test.mjs core/web/js/audio/mic-pipeline.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import { StreamingResampler } from "./resampler.js";
import {
    FRAME_BYTES,
    FRAME_SAMPLES,
    TARGET_SAMPLE_RATE,
    FrameAccumulator,
    downmixToMono,
    floatToPcm16Le,
    floatToPcm16Sample,
    clampFloat
} from "./pcm.js";

/**
 * @param {number} inRate
 * @param {number} freq
 * @param {number} durationSec
 * @param {number|number[]} blockSizes
 */
function feedTone(inRate, freq, durationSec, blockSizes) {
    const sizes = Array.isArray(blockSizes) ? blockSizes : [blockSizes];
    const r = new StreamingResampler(inRate, TARGET_SAMPLE_RATE);
    const acc = new FrameAccumulator();
    const totalIn = Math.floor(inRate * durationSec);
    /** @type {Float32Array[]} */
    const pcmFrames = [];
    let outSamples = 0;
    let seq = 0;
    let offset = 0;
    while (offset < totalIn) {
        const size = sizes[seq++ % sizes.length];
        const n = Math.min(size, totalIn - offset);
        const block = new Float32Array(n);
        for (let i = 0; i < n; i++) {
            block[i] = Math.sin(2 * Math.PI * freq * (offset + i) / inRate);
        }
        const out = r.process(block);
        outSamples += out.length;
        for (const frame of acc.push(out)) {
            pcmFrames.push(frame);
        }
        offset += n;
    }
    return { outSamples, pcmFrames, pending: acc.pendingSamples() };
}

function estimateFreq(samples, sampleRate) {
    const start = Math.floor(samples.length * 0.2);
    const end = Math.floor(samples.length * 0.8);
    let zc = 0;
    for (let i = start + 1; i < end; i++) {
        if (samples[i - 1] < 0 && samples[i] >= 0) zc++;
    }
    return zc / ((end - start) / sampleRate);
}

function maxBoundaryJump(inRate, freq, blockSize) {
    const r = new StreamingResampler(inRate, TARGET_SAMPLE_RATE);
    const total = Math.floor(inRate * 0.5);
    let prev = null;
    let maxJump = 0;
    let maxBoundary = 0;
    for (let offset = 0; offset < total; offset += blockSize) {
        const n = Math.min(blockSize, total - offset);
        const block = new Float32Array(n);
        for (let i = 0; i < n; i++) {
            block[i] = Math.sin(2 * Math.PI * freq * (offset + i) / inRate);
        }
        const out = r.process(block);
        for (let j = 0; j < out.length; j++) {
            if (prev != null) {
                const jump = Math.abs(out[j] - prev);
                maxJump = Math.max(maxJump, jump);
                if (j === 0) maxBoundary = Math.max(maxBoundary, jump);
            }
            prev = out[j];
        }
    }
    return { maxJump, maxBoundary };
}

test("1. 48 kHz mono input produces correct 960-sample / 1920-byte frames", () => {
    const { pcmFrames } = feedTone(48000, 1000, 1, 128);
    assert.equal(pcmFrames.length, 50);
    for (const frame of pcmFrames) {
        assert.equal(frame.length, FRAME_SAMPLES);
        assert.equal(floatToPcm16Le(frame).byteLength, FRAME_BYTES);
    }
});

test("2. 44.1 kHz mono becomes stable 48 kHz output", () => {
    const { outSamples, pcmFrames } = feedTone(44100, 1000, 1, 128);
    const expected = 44100 * (48000 / 44100);
    assert.ok(Math.abs(outSamples - expected) < 4);
    assert.ok(pcmFrames.length >= 49 && pcmFrames.length <= 50);
});

test("3. stereo planar downmix averages channels", () => {
    const left = new Float32Array([1, 0.5, -1]);
    const right = new Float32Array([-1, 0.5, 1]);
    assert.deepEqual([...downmixToMono([left, right])], [0, 0.5, 0]);
});

test("4. resampler state persists across irregular callback sizes", () => {
    const { outSamples } = feedTone(44100, 440, 0.5, [17, 64, 1, 256, 33, 128, 7]);
    const expected = Math.floor(44100 * 0.5) * (48000 / 44100);
    assert.ok(Math.abs(outSamples - expected) < 4);
});

test("5. frame accumulator emits no partial frames", () => {
    const acc = new FrameAccumulator();
    assert.equal(acc.push(new Float32Array(100)).length, 0);
    assert.equal(acc.push(new Float32Array(859)).length, 0);
    const frames = acc.push(new Float32Array(1));
    assert.equal(frames.length, 1);
    assert.equal(frames[0].length, 960);
    assert.equal(acc.pendingSamples(), 0);
});

test("6. ten seconds of 48 kHz input yields expected frame count", () => {
    const { pcmFrames, outSamples } = feedTone(48000, 1000, 10, 128);
    assert.equal(outSamples, 480000);
    assert.equal(pcmFrames.length, 500);
});

test("7. PCM16 conversion clamps correctly", () => {
    assert.equal(clampFloat(2), 1);
    assert.equal(clampFloat(-3), -1);
    assert.equal(floatToPcm16Sample(1), 32767);
    assert.equal(floatToPcm16Sample(-1), -32768);
});

test("8. output frame byte length is always 1920", () => {
    const { pcmFrames } = feedTone(44100, 500, 0.4, [128, 96, 200]);
    for (const frame of pcmFrames) {
        assert.equal(floatToPcm16Le(frame).byteLength, 1920);
    }
});

test("1000 Hz tone keeps pitch after 44.1→48 resampling", () => {
    const r = new StreamingResampler(44100, 48000);
    const all = [];
    const total = 44100;
    for (let offset = 0; offset < total; offset += 128) {
        const n = Math.min(128, total - offset);
        const block = new Float32Array(n);
        for (let i = 0; i < n; i++) {
            block[i] = Math.sin(2 * Math.PI * 1000 * (offset + i) / 44100);
        }
        all.push(...r.process(block));
    }
    const est = estimateFreq(all, 48000);
    assert.ok(Math.abs(est - 1000) < 15, `estFreq=${est}`);
    const durationErr = Math.abs(all.length / 48000 - 1);
    assert.ok(durationErr < 0.002, `durationErr=${durationErr}`);
});

test("no periodic discontinuities at callback boundaries", () => {
    const { maxJump, maxBoundary } = maxBoundaryJump(44100, 1000, 128);
    // Peak consecutive delta for 1 kHz @ 48 kHz ≈ 0.131
    assert.ok(maxJump < 0.25, `maxJump=${maxJump}`);
    assert.ok(maxBoundary < 0.25, `maxBoundary=${maxBoundary}`);
    assert.ok(Math.abs(maxBoundary - maxJump) < 0.05);
});

test("identity path copies rather than aliasing input", () => {
    const r = new StreamingResampler(48000, 48000);
    const input = new Float32Array([0.5, 0.25, -0.5]);
    const out = r.process(input);
    assert.notEqual(out, input);
    assert.deepEqual([...out], [...input]);
    input[0] = 9;
    assert.equal(out[0], 0.5);
});

test("Int16Array legacy packet is exactly 1920 bytes", () => {
    const frame = new Float32Array(FRAME_SAMPLES);
    const pcm = new Int16Array(FRAME_SAMPLES);
    for (let i = 0; i < FRAME_SAMPLES; i++) {
        pcm[i] = floatToPcm16Sample(frame[i]);
    }
    assert.equal(pcm.byteLength, FRAME_BYTES);
    assert.equal(pcm.buffer.byteLength, FRAME_BYTES);
});

const RATES = [8000, 16000, 22050, 24000, 32000, 44100, 48000, 88200, 96000];

for (const rate of RATES) {
    test(`10s @ ` + rate + ` Hz → ~10s of 48 kHz frames`, () => {
        const { outSamples, pcmFrames } = feedTone(rate, 1000, 10, [64, 128, 96, 17]);
        const expectedOut = Math.floor(rate * 10) * (48000 / rate);
        assert.ok(Math.abs(outSamples - expectedOut) < 8, `outSamples=` + outSamples);
        const expectedFrames = Math.floor(outSamples / FRAME_SAMPLES);
        assert.ok(Math.abs(pcmFrames.length - expectedFrames) <= 1);
        for (const frame of pcmFrames) {
            assert.equal(frame.length, FRAME_SAMPLES);
            assert.equal(floatToPcm16Le(frame).byteLength, FRAME_BYTES);
        }
    });

    test(`1 kHz tone pitch retained from ` + rate + ` Hz`, () => {
        const r = new StreamingResampler(rate, TARGET_SAMPLE_RATE);
        const all = [];
        const total = Math.floor(rate * 1.0);
        for (let offset = 0; offset < total; offset += 128) {
            const n = Math.min(128, total - offset);
            const block = new Float32Array(n);
            for (let i = 0; i < n; i++) {
                block[i] = Math.sin(2 * Math.PI * 1000 * (offset + i) / rate);
            }
            all.push(...r.process(block));
        }
        const est = estimateFreq(all, TARGET_SAMPLE_RATE);
        assert.ok(Math.abs(est - 1000) < 25, `rate=` + rate + ` est=` + est);
    });
}

test(`four-channel normalized downmix`, () => {
    const a = new Float32Array([1, 1]);
    const b = new Float32Array([1, -1]);
    const c = new Float32Array([-1, 1]);
    const d = new Float32Array([-1, -1]);
    assert.deepEqual([...downmixToMono([a, b, c, d])], [0, 0]);
});

test(`NaN and infinite samples are clamped`, () => {
    assert.equal(floatToPcm16Sample(Number.NaN), 0);
    assert.equal(floatToPcm16Sample(Number.POSITIVE_INFINITY), 32767);
    assert.equal(floatToPcm16Sample(Number.NEGATIVE_INFINITY), -32768);
});

test(`device rate change rebuilds resampler state`, () => {
    const r1 = new StreamingResampler(44100, 48000);
    r1.process(new Float32Array(128).fill(0.1));
    const r2 = new StreamingResampler(48000, 48000);
    const out = r2.process(new Float32Array([0.2, 0.3]));
    assert.equal(out.length, 2);
    assert.ok(Math.abs(out[0] - 0.2) < 1e-6);
    assert.ok(Math.abs(out[1] - 0.3) < 1e-6);
});
