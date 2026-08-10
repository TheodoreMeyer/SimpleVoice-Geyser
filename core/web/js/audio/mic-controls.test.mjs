/**
 * Mic worklet / processing invariants.
 * Run: node --test core/web/js/audio/mic-controls.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");
const micJs = fs.readFileSync(path.join(root, "js", "audio", "microphone.js"), "utf8");
const audioJs = fs.readFileSync(path.join(root, "js", "audio", "audio.js"), "utf8");

test("VAD hangover is approximately 400ms not ~48ms", () => {
    assert.match(micJs, /hangoverMs\s*=\s*400/);
    assert.doesNotMatch(micJs, /HANGOVER_FRAMES\s*=\s*18/);
});

test("worklet supports VAD bypass message", () => {
    assert.match(micJs, /setVadBypass/);
    assert.match(audioJs, /setVadBypass/);
});

test("getUserMedia uses ideal processing constraints", () => {
    assert.match(audioJs, /echoCancellation:\s*\{\s*ideal:/);
    assert.match(audioJs, /noiseSuppression:\s*\{\s*ideal:/);
    assert.match(audioJs, /autoGainControl:\s*\{\s*ideal:/);
});

test("rebuild queues follow-up instead of dropping prefs", () => {
    assert.match(audioJs, /micRebuildQueued/);
    assert.match(audioJs, /while \(this\.micRebuildQueued\)/);
});

test("diagnostic tone is opt-in via localStorage", () => {
    assert.match(audioJs, /svg\.debug\.tone/);
    assert.match(audioJs, /startDiagnosticTone/);
});

test("applied track settings are exposed separately from requested prefs", () => {
    assert.match(audioJs, /getAppliedMicStatus/);
    assert.match(audioJs, /appliedTrackSettings/);
});
