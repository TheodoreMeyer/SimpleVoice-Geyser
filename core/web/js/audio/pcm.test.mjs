/**
 * Additional PCM unit tests.
 * Run: node --test core/web/js/audio/pcm.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import {
    FRAME_BYTES,
    FRAME_SAMPLES,
    FrameAccumulator,
    downmixToMono,
    floatToPcm16Le,
    floatToPcm16Sample
} from "./pcm.js";

test("FRAME constants match server MicFrameCodec legacy size", () => {
    assert.equal(FRAME_SAMPLES, 960);
    assert.equal(FRAME_BYTES, 1920);
});

test("mono passthrough downmix returns same buffer", () => {
    const mono = new Float32Array([0.1, -0.2]);
    assert.equal(downmixToMono(mono, 1), mono);
    assert.equal(downmixToMono([mono])[0], mono[0]);
});

test("empty / invalid downmix yields empty", () => {
    assert.equal(downmixToMono(null).length, 0);
    assert.equal(downmixToMono([]).length, 0);
});

test("pcm16 buffer byte length is 2 per sample", () => {
    const samples = new Float32Array(FRAME_SAMPLES);
    const buf = floatToPcm16Le(samples);
    assert.equal(buf.byteLength, FRAME_BYTES);
});

test("accumulator reset clears remainder", () => {
    const acc = new FrameAccumulator();
    acc.push(new Float32Array(100));
    acc.reset();
    assert.equal(acc.pendingSamples(), 0);
});

test("signed scaling matches Int16 extremes", () => {
    assert.equal(floatToPcm16Sample(1), 0x7fff);
    assert.equal(floatToPcm16Sample(-1), -0x8000);
});
