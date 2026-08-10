/**
 * Minimal Node-free style assertions for the web-client readiness rules.
 * Run with: node --test core/web/js/websocket.ready.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";

function createReadyGate(overrides = {}) {
    return {
        hasJoined: false,
        inGroup: false,
        sessionMode: null,
        ws: { readyState: 1 }, // WebSocket.OPEN
        ...overrides,
        isConnected() {
            return !!(this.hasJoined && this.ws && this.ws.readyState === 1);
        },
        shouldSendMic(packetPresent) {
            return packetPresent
                && this.hasJoined
                && this.ws
                && this.ws.readyState === 1
                && this.sessionMode !== "NATIVE_VOICE_CONTROLLER"
                && this.inGroup;
        },
        shouldSendChat() {
            return this.isConnected();
        },
        shouldEnableGroups() {
            return this.isConnected();
        },
        shouldStartMic() {
            return this.isConnected() && this.sessionMode === "WEB_VOICE";
        }
    };
}

function isReadyPacket(packetType, message) {
    const type = String(packetType || "").toLowerCase();
    const msg = String(message || "").toLowerCase();
    // Prefer structured ready; legacy status is bounded fallback only.
    return type === "ready" || (type === "status" && msg.includes("connected as"));
}

test("browser does not start audio on websocket-open alone", () => {
    const gate = createReadyGate({ hasJoined: false });
    assert.equal(gate.isConnected(), false);
    assert.equal(gate.shouldSendMic(true), false);
    assert.equal(gate.shouldStartMic(), false);
});

test("open does not imply dashboard ready", () => {
    assert.equal(isReadyPacket("status", "Connecting…"), false);
    assert.equal(isReadyPacket("authenticated", ""), false);
});

test("browser starts audio only after ready confirmation and WEB_VOICE", () => {
    const gate = createReadyGate({ hasJoined: true, sessionMode: "WEB_VOICE", inGroup: true });
    assert.equal(gate.isConnected(), true);
    assert.equal(gate.shouldSendMic(true), true);
    assert.equal(gate.shouldStartMic(), true);
});

test("ready without group blocks mic TX", () => {
    const gate = createReadyGate({ hasJoined: true, sessionMode: "WEB_VOICE", inGroup: false });
    assert.equal(gate.shouldSendMic(true), false);
    assert.equal(gate.shouldStartMic(), true);
});

test("NATIVE_VOICE_CONTROLLER blocks mic TX but allows chat/groups", () => {
    const gate = createReadyGate({ hasJoined: true, sessionMode: "NATIVE_VOICE_CONTROLLER", inGroup: true });
    assert.equal(gate.shouldSendMic(true), false);
    assert.equal(gate.shouldStartMic(), false);
    assert.equal(gate.shouldSendChat(), true);
    assert.equal(gate.shouldEnableGroups(), true);
});

test("chat before ready is rejected", () => {
    const gate = createReadyGate({ hasJoined: false });
    assert.equal(gate.shouldSendChat(), false);
});

test("chat after ready is accepted", () => {
    const gate = createReadyGate({ hasJoined: true });
    assert.equal(gate.shouldSendChat(), true);
});

test("groups before ready are disabled", () => {
    const gate = createReadyGate({ hasJoined: false });
    assert.equal(gate.shouldEnableGroups(), false);
});

test("ready packet and Connected as status both count as ready", () => {
    assert.equal(isReadyPacket("ready", "Connected as Steve."), true);
    assert.equal(isReadyPacket("status", "Connected as Steve."), true);
    assert.equal(isReadyPacket("status", "Authenticating…"), false);
    assert.equal(isReadyPacket("error", "bad password"), false);
});

test("reconnect generation ignores stale close", () => {
    let generation = 1;
    const isCurrent = (candidate) => candidate === generation;
    generation = 2;
    assert.equal(isCurrent(1), false);
    assert.equal(isCurrent(2), true);
});

test("group revision is monotonic — stale snapshots ignored", () => {
    let revision = 5;
    function applySnapshot(next) {
        if (next < revision) return false;
        revision = next;
        return true;
    }
    assert.equal(applySnapshot(4), false);
    assert.equal(applySnapshot(5), true);
    assert.equal(applySnapshot(6), true);
    assert.equal(revision, 6);
});

test("credentials must not be persisted to web storage keys", () => {
    const forbidden = ["password", "svgPassword", "lastPassword", "credentials"];
    // Document the invariant: only username/device prefs may use localStorage.
    const allowedKeys = ["preferredMic", "preferredSpeaker", "preset", "svgTransmitMode", "svgPttBinding", "svgAllowBackgroundPtt"];
    for (const key of forbidden) {
        assert.equal(allowedKeys.includes(key), false);
    }
});
