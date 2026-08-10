/**
 * Capture production metrics tests.
 * Run: node --test core/web/js/audio/capture-metrics.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import {
    CaptureProductionMetrics,
    formatCaptureMetrics,
    OVERPRODUCTION_RATIO
} from "./capture-metrics.js";
import { StreamingResampler } from "./resampler.js";
import { FrameAccumulator, FRAME_SAMPLES, TARGET_SAMPLE_RATE } from "./pcm.js";

test("healthy 48 kHz capture reports ~50 fps and represented/wall ≈ 1", () => {
    const m = new CaptureProductionMetrics();
    m.reset(1);
    m.setRuntimeCounts({
        activeMediaStreams: 1,
        activeTracks: 1,
        activeLiveTracks: 1,
        activeAudioContexts: 1,
        activeWorkletNodes: 1,
        activeWorkletPorts: 1,
        activeWorkletMessageHandlers: 1,
        activeBrowserPipelines: 1,
        actualContextSampleRate: 48000,
        declaredInputRate: 48000,
        effectiveInputRate: 48000,
        actualChannelCount: 1
    });
    const wallMs = 1000;
    const source = 48000;
    const frames = 50;
    m.recordActive(0, source, source, source, frames, frames);
    const snap = m.snapshot(wallMs);
    assert.ok(snap);
    assert.ok(Math.abs(snap.pcmFramesPerSecond - 50) < 0.01);
    assert.ok(Math.abs(snap.representedAudioDurationPerWall - 1) < 0.01);
    assert.equal(snap.activeBrowserPipelines, 1);
    assert.match(formatCaptureMetrics(snap), /pcmFramesPerSecond=50/);
});

test("2× sample flood is overproduction; wall-clock calibration stays disabled", () => {
    const m = new CaptureProductionMetrics();
    m.reset(2);
    m.setRuntimeCounts({
        declaredInputRate: 48000,
        effectiveInputRate: 48000,
        actualContextSampleRate: 48000
    });
    m.recordActive(0, 96000, 96000, 96000, 100, 100);
    const snap = m.snapshot(1000);
    assert.ok(snap.measuredInputRate / snap.declaredInputRate >= 1.35);
    assert.ok(snap.pcmFramesPerSecond > 50 * OVERPRODUCTION_RATIO);
    assert.equal(m.suggestCalibratedInputRate(snap), null);
});

test("identity 48→48 resampler yields ~50 frames for 1s of 48k samples", () => {
    const r = new StreamingResampler(48000, TARGET_SAMPLE_RATE);
    const acc = new FrameAccumulator();
    let frames = 0;
    let outSamples = 0;
    const total = 48000;
    for (let offset = 0; offset < total; offset += 128) {
        const n = Math.min(128, total - offset);
        const block = new Float32Array(n);
        for (let i = 0; i < n; i++) {
            block[i] = Math.sin(2 * Math.PI * 1000 * (offset + i) / 48000);
        }
        const out = r.process(block);
        outSamples += out.length;
        frames += acc.push(out).length;
    }
    assert.ok(Math.abs(outSamples - 48000) < 8, `outSamples=${outSamples}`);
    assert.ok(frames >= 49 && frames <= 50, `frames=${frames}`);
});

test("production handler path: wall-clock reinterpretation stays disabled under 2× flood", () => {
    const declared = 48000;
    const metrics = new CaptureProductionMetrics();
    metrics.reset(3);
    metrics.setRuntimeCounts({
        declaredInputRate: declared,
        effectiveInputRate: declared,
        actualContextSampleRate: declared,
        activeBrowserPipelines: 1,
        activeWorkletMessageHandlers: 1,
        activeMediaStreams: 1,
        activeLiveTracks: 1,
        activeAudioContexts: 1,
        activeWorkletNodes: 1
    });

    let resampler = new StreamingResampler(declared, TARGET_SAMPLE_RATE);
    const acc = new FrameAccumulator();
    let pcmFrames = 0;
    const quantum = 128;
    const wallMs = 2000;
    const sourcePerSec = 96000;
    const totalSource = Math.floor(sourcePerSec * (wallMs / 1000));

    for (let offset = 0; offset < totalSource; offset += quantum) {
        const n = Math.min(quantum, totalSource - offset);
        const mono = new Float32Array(n);
        const resampled = resampler.process(mono);
        const frames = acc.push(resampled);
        pcmFrames += frames.length;
        metrics.recordActive(0, n, n, resampled.length, frames.length, frames.length);
    }

    const snap = metrics.snapshot(wallMs);
    assert.ok(snap.pcmFramesPerSecond > 90, `flood fps=${snap.pcmFramesPerSecond}`);
    assert.equal(metrics.suggestCalibratedInputRate(snap), null);

    resampler = new StreamingResampler(declared, TARGET_SAMPLE_RATE);
    const acc2 = new FrameAccumulator();
    let frames2 = 0;
    const normalSource = Math.floor(declared * (wallMs / 1000));
    for (let offset = 0; offset < normalSource; offset += quantum) {
        const n = Math.min(quantum, normalSource - offset);
        const mono = new Float32Array(n);
        frames2 += acc2.push(resampler.process(mono)).length;
    }
    const fps2 = frames2 / (wallMs / 1000);
    assert.ok(fps2 > 45 && fps2 < 55, `declared-rate fps=${fps2}`);
    assert.equal(pcmFrames > frames2, true, "mislabeled flood should over-produce vs declared rate");
});

test("ten-second generated signal through production objects ≈ 500 frames", () => {
    const r = new StreamingResampler(48000, TARGET_SAMPLE_RATE);
    const acc = new FrameAccumulator();
    let frames = 0;
    let outSamples = 0;
    const total = 480000;
    const sizes = [128, 96, 64, 256, 17, 200];
    let seq = 0;
    for (let offset = 0; offset < total; ) {
        const size = sizes[seq++ % sizes.length];
        const n = Math.min(size, total - offset);
        const block = new Float32Array(n);
        for (let i = 0; i < n; i++) {
            block[i] = Math.sin(2 * Math.PI * 1000 * (offset + i) / 48000);
        }
        const out = r.process(block);
        outSamples += out.length;
        frames += acc.push(out).length;
        offset += n;
    }
    assert.equal(outSamples, 480000);
    assert.equal(frames, 500);
    assert.equal(frames * FRAME_SAMPLES * 2, 960000);
});
