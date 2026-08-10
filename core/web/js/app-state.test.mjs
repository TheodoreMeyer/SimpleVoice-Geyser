/**
 * App state / dashboard transition tests.
 * Run: node --test core/web/js/app-state.test.mjs
 */
import test from "node:test";
import assert from "node:assert/strict";
import { AppState, AppStateController } from "./app-state.js";

function fakeViews() {
    const loginView = { hidden: false, setAttribute() {} };
    const dashboardView = { hidden: true, setAttribute() {} };
    const reconnectOverlay = { hidden: true, setAttribute() {} };
    return { loginView, dashboardView, reconnectOverlay };
}

test("1. initial state shows login and hides dashboard", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    assert.equal(app.getState(), AppState.LOGGED_OUT);
    assert.equal(views.loginView.hidden, false);
    assert.equal(views.dashboardView.hidden, true);
});

test("2. websocket open / connecting does not show dashboard", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.beginConnecting();
    assert.equal(app.getState(), AppState.CONNECTING);
    assert.equal(views.dashboardView.hidden, true);
    assert.equal(views.loginView.hidden, false);
});

test("3. structured ready shows dashboard", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.beginConnecting();
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE", playerName: "Steve" });
    assert.equal(app.getState(), AppState.READY_WEB_VOICE);
    assert.equal(views.dashboardView.hidden, false);
    assert.equal(views.loginView.hidden, true);
});

test("4. login view becomes hidden after ready", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE" });
    assert.equal(views.loginView.hidden, true);
});

test("5. session_mode before ready works", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ sessionMode: "NATIVE_VOICE_CONTROLLER" });
    assert.equal(app.getState(), AppState.LOGGED_OUT);
    assert.equal(views.dashboardView.hidden, true);
    app.updateFacts({ ready: true });
    assert.equal(app.getState(), AppState.READY_NATIVE_CONTROLLER);
    assert.equal(views.dashboardView.hidden, false);
});

test("6. session_mode after ready works", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ ready: true });
    assert.equal(app.getState(), AppState.READY_WEB_VOICE);
    app.updateFacts({ sessionMode: "NATIVE_VOICE_CONTROLLER" });
    assert.equal(app.getState(), AppState.READY_NATIVE_CONTROLLER);
    assert.equal(views.dashboardView.hidden, false);
});

test("7. group snapshot before ready is retained", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    const snap = { revision: 3, groups: [{ uuid: "a" }] };
    app.bufferGroupSnapshot(snap);
    assert.equal(app.takePendingGroupSnapshot(), snap);
    assert.equal(app.takePendingGroupSnapshot(), null);
});

test("8. ready dashboard remains visible when taking buffered snapshot", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.bufferGroupSnapshot({ revision: 1, groups: [] });
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE" });
    assert.equal(app.isDashboardVisible(), true);
    assert.ok(app.takePendingGroupSnapshot());
});

test("9. audio init failure must not revert ready state", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE" });
    // Simulate mic failure: state machine unchanged.
    assert.equal(app.getState(), AppState.READY_WEB_VOICE);
    assert.equal(views.dashboardView.hidden, false);
});

test("10. group loading failure keeps dashboard", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE" });
    assert.equal(app.isDashboardVisible(), true);
});

test("11. failed authentication keeps login visible", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.beginConnecting();
    app.failAuth();
    assert.equal(app.getState(), AppState.LOGGED_OUT);
    assert.equal(views.loginView.hidden, false);
    assert.equal(views.dashboardView.hidden, true);
});

test("12. logout restores login", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE", playerName: "Alex" });
    app.logout();
    assert.equal(app.getState(), AppState.LOGGED_OUT);
    assert.equal(views.loginView.hidden, false);
    assert.equal(views.dashboardView.hidden, true);
});

test("13. reconnect state keeps dashboard visible", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE" });
    app.beginReconnecting();
    assert.equal(app.getState(), AppState.RECONNECTING);
    assert.equal(views.dashboardView.hidden, false);
    assert.equal(views.reconnectOverlay.hidden, false);
});

test("14. initial page is not an error/disconnected state", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    assert.equal(app.getState(), AppState.LOGGED_OUT);
    assert.equal(app.facts.ready, false);
});

test("15. [hidden] visibility is driven solely by state", () => {
    const views = fakeViews();
    const app = new AppStateController(views);
    app.updateFacts({ ready: true, sessionMode: "WEB_VOICE" });
    assert.equal(views.dashboardView.hidden, false);
    app.logout();
    assert.equal(views.dashboardView.hidden, true);
});

test("ready packet detector prefers structured ready", () => {
    function isReadyPacket(packetType, message) {
        const type = String(packetType || "").toLowerCase();
        const msg = String(message || "").toLowerCase();
        return type === "ready" || (type === "status" && msg.includes("connected as"));
    }
    assert.equal(isReadyPacket("ready", ""), true);
    assert.equal(isReadyPacket("status", "Connected as Steve."), true);
    assert.equal(isReadyPacket("authenticated", ""), false);
    assert.equal(isReadyPacket("session_mode", ""), false);
});
