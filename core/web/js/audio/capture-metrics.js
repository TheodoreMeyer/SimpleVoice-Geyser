/**
 * DEBUG production metrics for mic capture.
 * Active-capture windows only — idle gaps are not averaged into rates.
 *
 * IMPORTANT: Do not use wall-clock throughput to reinterpret AudioContext.sampleRate.
 * A verified 48 kHz context is authoritative; ~100 fps means a duplicate path.
 */

import { FRAME_SAMPLES, TARGET_SAMPLE_RATE } from "./pcm.js";

/** Represented/wall ratio above this for a sustained window triggers fail-safe. */
export const OVERPRODUCTION_RATIO = 1.35;

/** Minimum active wall ms before production decisions. */
export const MIN_METRICS_WALL_MS = 800;

/** @deprecated Use MIN_METRICS_WALL_MS — kept for older test imports. */
export const MIN_CALIBRATION_WALL_MS = MIN_METRICS_WALL_MS;

/** Sustained overproduction windows before a controlled rebuild. */
export const OVERPRODUCTION_WINDOWS_BEFORE_REBUILD = 2;

/**
 * @typedef {object} CaptureSnapshot
 * @property {number} captureGeneration
 * @property {number} activeMediaStreams
 * @property {number} activeTracks
 * @property {number} activeLiveTracks
 * @property {number} activeAudioContexts
 * @property {number} activeWorkletNodes
 * @property {number} activeWorkletPorts
 * @property {number} activeWorkletMessageHandlers
 * @property {number} activeBrowserPipelines
 * @property {number} actualContextSampleRate
 * @property {number} actualTrackSampleRate
 * @property {number} actualChannelCount
 * @property {number} workletCallbacks
 * @property {number} sourceSamplesReceived
 * @property {number} monoSamplesProduced
 * @property {number} resampledSamplesProduced
 * @property {number} pcmFramesProduced
 * @property {number} binaryFramesSent
 * @property {number} wallIntervalMs
 * @property {number} audioDurationRepresentedMs
 * @property {number} sourceSamplesPerSecond
 * @property {number} resampledSamplesPerSecond
 * @property {number} pcmFramesPerSecond
 * @property {number} binaryFramesPerSecond
 * @property {number} representedAudioDurationPerWall
 * @property {number} declaredInputRate
 * @property {number} measuredInputRate
 * @property {number} effectiveInputRate
 */

/**
 * Tracks per-generation production counters during active capture.
 */
export class CaptureProductionMetrics {
    constructor() {
        this.reset(0);
        this.lastReportAt = 0;
        this.overproductionWindows = 0;
        this.rebuildsPerformed = 0;
        this.failSafeTripped = false;
    }

    /**
     * @param {number} generation
     */
    reset(generation) {
        this.generation = generation | 0;
        this.windowStartedAt = -1;
        this.workletCallbacks = 0;
        this.sourceSamplesReceived = 0;
        this.monoSamplesProduced = 0;
        this.resampledSamplesProduced = 0;
        this.pcmFramesProduced = 0;
        this.binaryFramesSent = 0;
        this.declaredInputRate = TARGET_SAMPLE_RATE;
        this.effectiveInputRate = TARGET_SAMPLE_RATE;
        this.actualContextSampleRate = TARGET_SAMPLE_RATE;
        this.actualTrackSampleRate = 0;
        this.actualChannelCount = 1;
        this.activeMediaStreams = 0;
        this.activeTracks = 0;
        this.activeLiveTracks = 0;
        this.activeAudioContexts = 0;
        this.activeWorkletNodes = 0;
        this.activeWorkletPorts = 0;
        this.activeWorkletMessageHandlers = 0;
        this.activeBrowserPipelines = 0;
        this.calibrated = false;
    }

    /**
     * @param {object} counts
     */
    setRuntimeCounts(counts) {
        this.activeMediaStreams = counts.activeMediaStreams | 0;
        this.activeTracks = counts.activeTracks | 0;
        this.activeLiveTracks = counts.activeLiveTracks | 0;
        this.activeAudioContexts = counts.activeAudioContexts | 0;
        this.activeWorkletNodes = counts.activeWorkletNodes | 0;
        this.activeWorkletPorts = counts.activeWorkletPorts | 0;
        this.activeWorkletMessageHandlers = counts.activeWorkletMessageHandlers | 0;
        this.activeBrowserPipelines = counts.activeBrowserPipelines | 0;
        if (Number.isFinite(counts.actualContextSampleRate)) {
            this.actualContextSampleRate = counts.actualContextSampleRate;
        }
        if (Number.isFinite(counts.actualTrackSampleRate)) {
            this.actualTrackSampleRate = counts.actualTrackSampleRate;
        }
        if (Number.isFinite(counts.actualChannelCount)) {
            this.actualChannelCount = counts.actualChannelCount;
        }
        if (Number.isFinite(counts.declaredInputRate)) {
            this.declaredInputRate = counts.declaredInputRate;
        }
        if (Number.isFinite(counts.effectiveInputRate)) {
            this.effectiveInputRate = counts.effectiveInputRate;
        }
    }

    /**
     * @param {number} nowMs
     * @param {number} sourceSamples
     * @param {number} monoSamples
     * @param {number} resampledSamples
     * @param {number} pcmFrames
     * @param {number} binaryFrames
     */
    recordActive(nowMs, sourceSamples, monoSamples, resampledSamples, pcmFrames, binaryFrames) {
        if (this.windowStartedAt < 0) {
            this.windowStartedAt = nowMs;
        }
        this.workletCallbacks++;
        this.sourceSamplesReceived += sourceSamples | 0;
        this.monoSamplesProduced += monoSamples | 0;
        this.resampledSamplesProduced += resampledSamples | 0;
        this.pcmFramesProduced += pcmFrames | 0;
        this.binaryFramesSent += binaryFrames | 0;
    }

    /**
     * @param {number} nowMs
     * @returns {CaptureSnapshot|null}
     */
    snapshot(nowMs) {
        if (this.windowStartedAt < 0) {
            return null;
        }
        const wallIntervalMs = Math.max(1, nowMs - this.windowStartedAt);
        const wallSec = wallIntervalMs / 1000;
        const audioDurationRepresentedMs =
            (this.pcmFramesProduced * FRAME_SAMPLES / TARGET_SAMPLE_RATE) * 1000;
        const measuredInputRate = this.sourceSamplesReceived / wallSec;
        return {
            captureGeneration: this.generation,
            activeMediaStreams: this.activeMediaStreams,
            activeTracks: this.activeTracks,
            activeLiveTracks: this.activeLiveTracks,
            activeAudioContexts: this.activeAudioContexts,
            activeWorkletNodes: this.activeWorkletNodes,
            activeWorkletPorts: this.activeWorkletPorts,
            activeWorkletMessageHandlers: this.activeWorkletMessageHandlers,
            activeBrowserPipelines: this.activeBrowserPipelines,
            actualContextSampleRate: this.actualContextSampleRate,
            actualTrackSampleRate: this.actualTrackSampleRate,
            actualChannelCount: this.actualChannelCount,
            workletCallbacks: this.workletCallbacks,
            sourceSamplesReceived: this.sourceSamplesReceived,
            monoSamplesProduced: this.monoSamplesProduced,
            resampledSamplesProduced: this.resampledSamplesProduced,
            pcmFramesProduced: this.pcmFramesProduced,
            binaryFramesSent: this.binaryFramesSent,
            wallIntervalMs,
            audioDurationRepresentedMs,
            sourceSamplesPerSecond: this.sourceSamplesReceived / wallSec,
            resampledSamplesPerSecond: this.resampledSamplesProduced / wallSec,
            pcmFramesPerSecond: this.pcmFramesProduced / wallSec,
            binaryFramesPerSecond: this.binaryFramesSent / wallSec,
            representedAudioDurationPerWall: audioDurationRepresentedMs / wallIntervalMs,
            declaredInputRate: this.declaredInputRate,
            measuredInputRate,
            effectiveInputRate: this.effectiveInputRate
        };
    }

    /**
     * Begin a fresh active window after reporting (keeps generation/runtime counts).
     * @param {number} nowMs
     */
    rollWindow(nowMs) {
        this.windowStartedAt = nowMs;
        this.workletCallbacks = 0;
        this.sourceSamplesReceived = 0;
        this.monoSamplesProduced = 0;
        this.resampledSamplesProduced = 0;
        this.pcmFramesProduced = 0;
        this.binaryFramesSent = 0;
    }

    /**
     * Wall-clock rate reinterpretation is intentionally disabled.
     * @param {CaptureSnapshot} _snap
     * @returns {null}
     */
    suggestCalibratedInputRate(_snap) {
        return null;
    }

    /**
     * @param {CaptureSnapshot} snap
     * @returns {boolean} true when fail-safe should rebuild
     */
    noteOverproduction(snap) {
        if (!snap || snap.wallIntervalMs < MIN_METRICS_WALL_MS) {
            return false;
        }
        if (snap.representedAudioDurationPerWall > OVERPRODUCTION_RATIO
            || snap.pcmFramesPerSecond > 50 * OVERPRODUCTION_RATIO) {
            this.overproductionWindows++;
        } else if (snap.representedAudioDurationPerWall < 1.1) {
            this.overproductionWindows = Math.max(0, this.overproductionWindows - 1);
        }
        if (this.overproductionWindows >= OVERPRODUCTION_WINDOWS_BEFORE_REBUILD
            && this.rebuildsPerformed < 1) {
            this.rebuildsPerformed++;
            this.overproductionWindows = 0;
            return true;
        }
        if (this.rebuildsPerformed >= 1
            && this.overproductionWindows >= OVERPRODUCTION_WINDOWS_BEFORE_REBUILD) {
            this.failSafeTripped = true;
        }
        return false;
    }
}

/**
 * Format a DEBUG metrics line.
 * @param {CaptureSnapshot} snap
 * @returns {string}
 */
export function formatCaptureMetrics(snap) {
    if (!snap) {
        return "[Audio] capture-metrics idle";
    }
    return (
        `[Audio] capture-metrics gen=${snap.captureGeneration}`
        + ` streams=${snap.activeMediaStreams}`
        + ` tracks=${snap.activeTracks}`
        + ` liveTracks=${snap.activeLiveTracks}`
        + ` ctx=${snap.activeAudioContexts}`
        + ` worklets=${snap.activeWorkletNodes}`
        + ` ports=${snap.activeWorkletPorts}`
        + ` handlers=${snap.activeWorkletMessageHandlers}`
        + ` pipelines=${snap.activeBrowserPipelines}`
        + ` contextRate=${snap.actualContextSampleRate}`
        + ` trackRate=${snap.actualTrackSampleRate || "n/a"}`
        + ` channels=${snap.actualChannelCount}`
        + ` workletCallbacks=${snap.workletCallbacks}`
        + ` sourceSamples=${snap.sourceSamplesReceived}`
        + ` monoSamples=${snap.monoSamplesProduced}`
        + ` resampledSamples=${snap.resampledSamplesProduced}`
        + ` pcmFrames=${snap.pcmFramesProduced}`
        + ` binaryFrames=${snap.binaryFramesSent}`
        + ` wallIntervalMs=${snap.wallIntervalMs.toFixed(0)}`
        + ` audioDurationRepresentedMs=${snap.audioDurationRepresentedMs.toFixed(0)}`
        + ` sourceSamplesPerSecond=${snap.sourceSamplesPerSecond.toFixed(1)}`
        + ` resampledSamplesPerSecond=${snap.resampledSamplesPerSecond.toFixed(1)}`
        + ` pcmFramesPerSecond=${snap.pcmFramesPerSecond.toFixed(2)}`
        + ` binaryFramesPerSecond=${snap.binaryFramesPerSecond.toFixed(2)}`
        + ` represented/wall=${snap.representedAudioDurationPerWall.toFixed(3)}`
        + ` declaredIn=${snap.declaredInputRate}`
        + ` measuredIn=${snap.measuredInputRate.toFixed(1)}`
        + ` effectiveIn=${snap.effectiveInputRate}`
    );
}
