/**
 * Tests for StreamingResampler — no sleeps; deterministic DSP checks.
 * Run: node --test core/web/js/audio/resampler.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import { StreamingResampler, hermiteInterpolate, linearInterpolate } from "./resampler.js";
import { FRAME_SAMPLES, FrameAccumulator, downmixToMono, floatToPcm16Le, floatToPcm16Sample, clampFloat } from "./pcm.js";

test("hermite and linear interpolators are finite", () => {
    assert.equal(linearInterpolate(0, 0, 1), 0);
    assert.equal(linearInterpolate(1, 0, 1), 1);
    assert.equal(linearInterpolate(0.5, 0, 2), 1);
    const h = hermiteInterpolate(0.5, 0, 1, 2, 3);
    assert.ok(Number.isFinite(h));
});

test("identity rate passthrough returns copied samples", () => {
    const r = new StreamingResampler(48000, 48000);
    const input = new Float32Array([0, 0.25, -0.5, 1]);
    const out = r.process(input);
    assert.equal(out.length, 4);
    assert.notEqual(out, input);
    assert.deepEqual([...out], [...input]);
});

test("silence stays near zero through 44.1→48 streaming", () => {
    const r = new StreamingResampler(44100, 48000);
    const block = new Float32Array(256);
    let total = 0;
    let count = 0;
    for (let i = 0; i < 40; i++) {
        const out = r.process(block);
        for (let j = 0; j < out.length; j++) {
            total += Math.abs(out[j]);
            count++;
        }
    }
    assert.ok(count > 0);
    assert.ok(total / count < 1e-6);
});

test("440Hz tone survives 44.1→48 without catastrophic amplitude loss", () => {
    const inRate = 44100;
    const outRate = 48000;
    const r = new StreamingResampler(inRate, outRate);
    const freq = 440;
    const durationSec = 0.2;
    const totalIn = Math.floor(inRate * durationSec);
    const blockSize = 128;

    let peakIn = 0;
    let peakOut = 0;
    let outCount = 0;

    for (let offset = 0; offset < totalIn; offset += blockSize) {
        const n = Math.min(blockSize, totalIn - offset);
        const block = new Float32Array(n);
        for (let i = 0; i < n; i++) {
            const t = (offset + i) / inRate;
            block[i] = Math.sin(2 * Math.PI * freq * t);
            peakIn = Math.max(peakIn, Math.abs(block[i]));
        }
        const out = r.process(block);
        outCount += out.length;
        for (let i = 0; i < out.length; i++) {
            peakOut = Math.max(peakOut, Math.abs(out[i]));
        }
    }

    const expectedOut = Math.floor(totalIn * (outRate / inRate)) - 4;
    // Streaming may leave a few samples buffered; allow small slack, no long-term drift collapse.
    assert.ok(outCount >= expectedOut - 8, `outCount=${outCount} expected~${expectedOut}`);
    assert.ok(outCount <= expectedOut + 16, `outCount=${outCount} drifted high`);
    assert.ok(peakOut > 0.85 * peakIn, `peakOut=${peakOut} peakIn=${peakIn}`);
});

test("stereo planar downmix averages channels", () => {
    const left = new Float32Array([1, 0.5, -1]);
    const right = new Float32Array([-1, 0.5, 1]);
    const mono = downmixToMono([left, right]);
    assert.deepEqual([...mono], [0, 0.5, 0]);
});

test("interleaved stereo is not treated as consecutive mono", () => {
    // L0,R0,L1,R1 → mono averages pairs: (1+-1)/2=0, (0.5+0.5)/2=0.5
    const interleaved = new Float32Array([1, -1, 0.5, 0.5]);
    const mono = downmixToMono(interleaved, 2);
    assert.equal(mono.length, 2);
    assert.deepEqual([...mono], [0, 0.5]);
});

test("44.1→48 streaming across partial blocks has no cumulative sample-count drift", () => {
    const r = new StreamingResampler(44100, 48000);
    const sizes = [17, 64, 1, 256, 33, 128, 7];
    let inSamples = 0;
    let outSamples = 0;
    let seq = 0;
    for (let round = 0; round < 30; round++) {
        for (const size of sizes) {
            const block = new Float32Array(size);
            for (let i = 0; i < size; i++) {
                block[i] = ((seq++ % 100) / 100) - 0.5;
            }
            inSamples += size;
            outSamples += r.process(block).length;
        }
    }
    const expected = inSamples * (48000 / 44100);
    const err = Math.abs(outSamples - expected);
    // Fractional phase carry must keep error bounded (not growing with time).
    assert.ok(err < 4, `sample count error ${err} (in=${inSamples} out=${outSamples} expected≈${expected})`);
});

test("FrameAccumulator emits only complete 960-sample frames and keeps remainder", () => {
    const acc = new FrameAccumulator(FRAME_SAMPLES);
    const a = acc.push(new Float32Array(500));
    assert.equal(a.length, 0);
    assert.equal(acc.pendingSamples(), 500);

    const b = acc.push(new Float32Array(500));
    assert.equal(b.length, 1);
    assert.equal(b[0].length, 960);
    assert.equal(acc.pendingSamples(), 40);

    const c = acc.push(new Float32Array(920));
    assert.equal(c.length, 1);
    assert.equal(c[0].length, 960);
    assert.equal(acc.pendingSamples(), 0);
});

test("exactly 960 samples yields one frame", () => {
    const acc = new FrameAccumulator();
    const frames = acc.push(new Float32Array(960));
    assert.equal(frames.length, 1);
    assert.equal(frames[0].length, 960);
    assert.equal(acc.pendingSamples(), 0);
});

test("floatToPcm16 clamps and scales correctly little-endian", () => {
    assert.equal(clampFloat(2), 1);
    assert.equal(clampFloat(-3), -1);
    assert.equal(floatToPcm16Sample(1), 32767);
    assert.equal(floatToPcm16Sample(-1), -32768);
    assert.equal(floatToPcm16Sample(0), 0);

    const buf = floatToPcm16Le(new Float32Array([1, -1, 0.5]));
    const view = new DataView(buf);
    assert.equal(view.getInt16(0, true), 32767);
    assert.equal(view.getInt16(2, true), -32768);
    assert.equal(view.getInt16(4, true), floatToPcm16Sample(0.5));
});

test("resampler reset clears phase and buffer", () => {
    const r = new StreamingResampler(44100, 48000);
    r.process(new Float32Array(100).fill(0.1));
    r.reset();
    assert.equal(r.position, 0);
    assert.equal(r.buffer.length, 0);
    assert.equal(r.consumed, 0);
});
