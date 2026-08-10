/**
 * Authoritative frontend application state for login ↔ dashboard transitions.
 * View visibility must only be driven through this controller.
 */

export const AppState = Object.freeze({
    LOGGED_OUT: "LOGGED_OUT",
    CONNECTING: "CONNECTING",
    AUTHENTICATING: "AUTHENTICATING",
    READY_WEB_VOICE: "READY_WEB_VOICE",
    READY_NATIVE_CONTROLLER: "READY_NATIVE_CONTROLLER",
    RECONNECTING: "RECONNECTING",
    ERROR: "ERROR"
});

/**
 * @typedef {typeof AppState[keyof typeof AppState]} AppStateName
 */

/**
 * @typedef {object} AppStateViews
 * @property {HTMLElement} loginView
 * @property {HTMLElement} dashboardView
 * @property {HTMLElement} [reconnectOverlay]
 */

/**
 * @typedef {object} SessionFacts
 * @property {boolean} authenticated
 * @property {boolean} ready
 * @property {string|null} sessionMode
 * @property {string} playerName
 */

export class AppStateController {
    /**
     * @param {AppStateViews} views
     * @param {{ onStateChange?: (from: AppStateName, to: AppStateName) => void }} [options]
     */
    constructor(views, options = {}) {
        this.views = views;
        this.onStateChange = options.onStateChange || null;
        /** @type {AppStateName} */
        this.state = AppState.LOGGED_OUT;
        /** @type {SessionFacts} */
        this.facts = {
            authenticated: false,
            ready: false,
            sessionMode: null,
            playerName: ""
        };
        /** @type {object|null} */
        this.pendingGroupSnapshot = null;
        this.apply(AppState.LOGGED_OUT, { force: true });
    }

    /**
     * @returns {AppStateName}
     */
    getState() {
        return this.state;
    }

    /**
     * @returns {boolean}
     */
    isDashboardVisible() {
        return this.state === AppState.READY_WEB_VOICE
            || this.state === AppState.READY_NATIVE_CONTROLLER
            || this.state === AppState.RECONNECTING;
    }

    /**
     * @returns {boolean}
     */
    isReady() {
        return this.state === AppState.READY_WEB_VOICE
            || this.state === AppState.READY_NATIVE_CONTROLLER;
    }

    /**
     * @returns {boolean}
     */
    isWebVoice() {
        return this.state === AppState.READY_WEB_VOICE
            || (this.facts.ready && this.facts.sessionMode === "WEB_VOICE");
    }

    /**
     * Record independent protocol facts. May transition to a ready dashboard state.
     * @param {Partial<SessionFacts>} patch
     * @returns {AppStateName}
     */
    updateFacts(patch) {
        if (patch.authenticated != null) {
            this.facts.authenticated = !!patch.authenticated;
            if (this.facts.authenticated) {
                this.#traceProtocol("authenticated");
            }
        }
        if (patch.ready != null) {
            this.facts.ready = !!patch.ready;
            if (this.facts.ready) {
                this.#traceProtocol("ready");
            }
        }
        if (Object.prototype.hasOwnProperty.call(patch, "sessionMode")) {
            const mode = patch.sessionMode
                ? String(patch.sessionMode).toUpperCase()
                : null;
            this.facts.sessionMode = mode;
            if (mode) {
                this.#traceProtocol(`session_mode=${mode}`);
            }
        }
        if (patch.playerName != null) {
            this.facts.playerName = String(patch.playerName || "");
        }

        return this.#reconcileFromFacts();
    }

    /**
     * Buffer a groups snapshot until the dashboard is visible.
     * @param {object} snapshot
     */
    bufferGroupSnapshot(snapshot) {
        this.pendingGroupSnapshot = snapshot || null;
    }

    /**
     * @returns {object|null}
     */
    takePendingGroupSnapshot() {
        const snap = this.pendingGroupSnapshot;
        this.pendingGroupSnapshot = null;
        return snap;
    }

    /**
     * @param {AppStateName} next
     * @param {{ force?: boolean, errorMessage?: string }} [options]
     * @returns {AppStateName}
     */
    apply(next, options = {}) {
        if (!Object.values(AppState).includes(next)) {
            return this.state;
        }
        if (!options.force && next === this.state) {
            this.#applyVisibility(next);
            return this.state;
        }

        const from = this.state;
        this.state = next;
        this.#traceUi(`${from} → ${next}`);
        this.#applyVisibility(next);
        this.onStateChange?.(from, next);
        return this.state;
    }

    beginConnecting() {
        this.facts.authenticated = false;
        this.facts.ready = false;
        this.facts.sessionMode = null;
        this.pendingGroupSnapshot = null;
        return this.apply(AppState.CONNECTING);
    }

    beginAuthenticating() {
        if (this.state === AppState.CONNECTING || this.state === AppState.LOGGED_OUT) {
            return this.apply(AppState.AUTHENTICATING);
        }
        return this.state;
    }

    beginReconnecting() {
        return this.apply(AppState.RECONNECTING);
    }

    failAuth() {
        this.facts.authenticated = false;
        this.facts.ready = false;
        this.facts.sessionMode = null;
        this.pendingGroupSnapshot = null;
        // Stay on the login card; caller sets the credential error message.
        return this.apply(AppState.LOGGED_OUT);
    }

    logout() {
        this.facts = {
            authenticated: false,
            ready: false,
            sessionMode: null,
            playerName: ""
        };
        this.pendingGroupSnapshot = null;
        return this.apply(AppState.LOGGED_OUT);
    }

    /**
     * Unexpected disconnect after a ready session.
     * Prefer reconnecting UI when the socket layer will retry.
     * @param {{ willReconnect?: boolean }} [options]
     */
    connectionLost(options = {}) {
        this.facts.ready = false;
        this.facts.authenticated = false;
        if (options.willReconnect) {
            return this.apply(AppState.RECONNECTING);
        }
        this.facts.sessionMode = null;
        this.pendingGroupSnapshot = null;
        return this.apply(AppState.ERROR);
    }

    #reconcileFromFacts() {
        if (!this.facts.ready) {
            // Stay in connecting/authenticating/reconnecting until ready.
            if (this.state === AppState.RECONNECTING) {
                return this.state;
            }
            if (this.facts.authenticated && this.state === AppState.CONNECTING) {
                return this.apply(AppState.AUTHENTICATING);
            }
            return this.state;
        }

        const mode = this.facts.sessionMode;
        if (mode === "NATIVE_VOICE_CONTROLLER") {
            return this.apply(AppState.READY_NATIVE_CONTROLLER);
        }
        if (mode === "WEB_VOICE") {
            return this.apply(AppState.READY_WEB_VOICE);
        }

        // Ready without session_mode yet: enter web-voice shell, refine when mode arrives.
        return this.apply(AppState.READY_WEB_VOICE);
    }

    /**
     * Sole owner of primary view visibility.
     * @param {AppStateName} state
     */
    #applyVisibility(state) {
        const { loginView, dashboardView, reconnectOverlay } = this.views;
        const showDashboard = state === AppState.READY_WEB_VOICE
            || state === AppState.READY_NATIVE_CONTROLLER
            || state === AppState.RECONNECTING;
        const showLogin = !showDashboard;

        this.#setHidden(loginView, !showLogin);
        this.#setHidden(dashboardView, !showDashboard);

        if (reconnectOverlay) {
            this.#setHidden(reconnectOverlay, state !== AppState.RECONNECTING);
        }
    }

    /**
     * @param {HTMLElement|null|undefined} el
     * @param {boolean} hidden
     */
    #setHidden(el, hidden) {
        if (!el) return;
        el.hidden = !!hidden;
        el.setAttribute("aria-hidden", hidden ? "true" : "false");
    }

    #traceUi(message) {
        console.debug(`SVG UI state: ${message}`);
    }

    #traceProtocol(message) {
        console.debug(`SVG protocol: ${message}`);
    }
}
