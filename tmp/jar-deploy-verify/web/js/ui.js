import { Logger } from "./utils/logger.js";
import { PttController } from "./ptt.js";
import { GroupsController } from "./groups.js";
import { AppState, AppStateController } from "./app-state.js";
import { DashboardLayoutController } from "./dashboard-layout.js";
import { AppearanceController } from "./appearance.js";
import {
    FRONTEND_BUILD_ID,
    PROTOCOL_VERSION,
    FRONTEND_SCHEMA,
    compareBuildIdentity,
    reloadUpdatedClientOnce
} from "./build-identity.js";

/**
 * @import {SvgUIOptions} from "./utils/internal-types.js"
 * @import {PttElements} from "./utils/types.js"
 */

export class SvgUI {

    /** @type {import("./utils/types.js").FormElements} */
    form;
    /** @type {import("./utils/types.js").AudioElements} */
    audio;
    /** @type {import("./utils/types.js").PttElements} */
    ptt;
    /** @type {import("./utils/types.js").DevElements} */
    dev;
    /** @type {import("./utils/types.js").ViewElements} */
    views;
    /** @type {import("./utils/types.js").DashboardElements} */
    dashboard;
    /** @type {import("./utils/types.js").GroupElements} */
    groups;
    /** @type {import("./utils/types.js").ChatElements|null} */
    chat;

    /**
     * @param {SvgUIOptions} options
     */
    constructor(options = {}) {
        this.webSocketController = options.webSocketController;
        this.audioRuntime = options.audioRuntime;
        this.audioController = options.audioController;

        this.form = options.form;
        this.audio = options.audio;
        this.ptt = options.ptt;
        this.dev = options.dev;
        this.views = options.views;
        this.dashboard = options.dashboard;
        this.groups = options.groups;
        this.chat = options.chat || null;

        this.pttController = null;
        this.groupsController = null;
        /** @type {AppStateController|null} */
        this.appState = null;
        /** @type {DashboardLayoutController|null} */
        this.layoutController = null;
        /** @type {AppearanceController|null} */
        this.appearanceController = null;
        this.micErrorEl = null;
        this.groupsErrorEl = null;
        this.voiceGateEl = null;
        this.joinButtonDefaultLabel = "Log In";
        this.voiceTransmitEnabled = false;
        this.privacyMuted = false;
        this.micGraphGenerationAtLayout = 0;
        this.buildMatch = null;
        this.serverBuildId = null;
    }

    #initAppearance() {
        const dashRoot = this.views.dashboardView
            || document.getElementById("dashboard-view");
        if (!dashRoot || this.appearanceController) {
            return;
        }
        const genBeforeTheme = this.audioController.getActiveMicGeneration?.() || 0;
        this.appearanceController = new AppearanceController({
            dashboardRoot: dashRoot,
            accentSelectEl: this.dashboard.appearanceAccentGroup || null,
            borderSelectEl: this.dashboard.appearanceBorderGroup || null,
            accentSwatchGroupEl: this.dashboard.appearanceAccentSwatches || null,
            borderSwatchGroupEl: this.dashboard.appearanceBorderSwatches || null,
            resetBtn: this.dashboard.resetAppearanceBtn || null,
            onChange: () => {
                const gen = this.audioController.getActiveMicGeneration?.() || 0;
                if (genBeforeTheme && gen !== genBeforeTheme) {
                    console.warn("SVG appearance: microphone generation changed unexpectedly during theme change");
                }
            }
        });
        this.appearanceController.init();
    }

    #setBuildDiagText(selector, value) {
        const el = document.querySelector(`[data-svg="${selector}"]`);
        if (el) {
            el.textContent = value == null || value === "" ? "—" : String(value);
        }
    }

    #populateBuildDiagnostics(opts = {}) {
        const serverBuild = opts.serverBuildId ?? this.serverBuildId;
        const protocol = opts.protocolVersion ?? PROTOCOL_VERSION;
        const comparison = compareBuildIdentity(serverBuild, opts.protocolVersion ?? null);
        this.buildMatch = comparison;
        this.#setBuildDiagText("dev.frontend-build", FRONTEND_BUILD_ID);
        this.#setBuildDiagText("dev.server-build", serverBuild || "—");
        this.#setBuildDiagText("dev.protocol", Number.isFinite(protocol) ? protocol : "—");
        this.#setBuildDiagText("dev.assets-build", FRONTEND_BUILD_ID);
        this.#setBuildDiagText(
            "dev.build-match",
            comparison.match ? "true" : `false (${comparison.reason})`
        );
        this.#setBuildDiagText(
            "dev.worklet-build",
            opts.workletBuildId || this.audioController?.workletBuildId || window.WORKLET_BUILD_ID || "—"
        );
        this.#setBuildDiagText(
            "dev.decoder-build",
            opts.decoderBuildId || window.DECODER_BUILD_ID || "—"
        );

        console.info(
            `[SVG Build] frontend=${FRONTEND_BUILD_ID} server=${serverBuild || "—"}`
            + ` protocol=${protocol} schema=${FRONTEND_SCHEMA} match=${comparison.match}`
        );

        const banner = this.dashboard.buildMismatchBanner
            || document.querySelector('[data-svg="dash.build-mismatch"]');
        const showBanner = !comparison.match
            && comparison.reason !== "frontend_unstamped"
            && comparison.reason !== "server_missing";
        if (banner) {
            banner.hidden = !showBanner;
        }
        this.#applyBuildMatchGates();
    }

    #isBuildMatched() {
        // Source/dev trees keep @@ placeholders — allow local work without a stamped JAR.
        if (!this.buildMatch) {
            return true;
        }
        if (this.buildMatch.reason === "frontend_unstamped") {
            return true;
        }
        if (this.buildMatch.reason === "server_missing") {
            return true;
        }
        return !!this.buildMatch.match;
    }

    #applyBuildMatchGates() {
        const matched = this.#isBuildMatched();
        if (!matched) {
            this.audioController?.stopMic?.();
            this.groupsController?.setEnabled(false);
        } else if (this.appState?.isReady?.()) {
            this.groupsController?.setEnabled(true);
        }
    }

    #wireBuildMismatchControls() {
        const reloadBtn = this.dashboard.reloadClientBtn
            || document.querySelector('[data-svg="dash.reload-client"]');
        reloadBtn?.addEventListener("click", (event) => {
            event.preventDefault();
            reloadUpdatedClientOnce(FRONTEND_BUILD_ID);
        });
        this.#populateBuildDiagnostics();
    }

    setSelectUnavailable(select, label) {
        select.innerHTML = "";

        const option = document.createElement("option");
        option.disabled = true;
        option.selected = true;
        option.textContent = label;

        select.appendChild(option);
        select.disabled = true;
    }

    async populateAudioDevices() {
        const { micSelect, speakerSelect } = this.audio;

        const {
            microphones,
            speakers,
            available,
            reason
        } = await this.audioController.getAudioDevices();

        micSelect.innerHTML = "";
        speakerSelect.innerHTML = "";

        if (!available) {
            const fallbackReason =
                reason || "Audio device APIs are unavailable.";

            this.setSelectUnavailable(micSelect, "Microphone unavailable");
            this.setSelectUnavailable(speakerSelect, "Speaker unavailable");

            Logger.log(`[Audio] ${fallbackReason}`);
            return;
        }

        for (const mic of microphones) {
            const option = document.createElement("option");
            option.value = mic.deviceId;
            option.textContent = mic.label || `Microphone ${micSelect.options.length + 1}`;
            micSelect.appendChild(option);
        }

        for (const speaker of speakers) {
            const option = document.createElement("option");
            option.value = speaker.deviceId;
            option.textContent = speaker.label || `Speaker ${speakerSelect.options.length + 1}`;
            speakerSelect.appendChild(option);
        }

        if (microphones.length === 0) {
            this.setSelectUnavailable(micSelect, "No microphones detected");
        } else {
            micSelect.disabled = false;
        }

        if (speakers.length === 0) {
            this.setSelectUnavailable(speakerSelect, "No speakers detected");
        } else {
            speakerSelect.disabled = false;
        }

        const savedMic = localStorage.getItem("preferredMic");
        const savedSpeaker = localStorage.getItem("preferredSpeaker");

        if (savedMic && microphones.some((d) => d.deviceId === savedMic)) {
            micSelect.value = savedMic;
        }

        if (savedSpeaker && speakers.some((d) => d.deviceId === savedSpeaker)) {
            speakerSelect.value = savedSpeaker;
            await this.audioController.setOutputDevice(savedSpeaker);
        }
    }

    /**
     * Status panel semantics — never red before a real failure.
     * @param {string} text
     * @param {"idle"|"connecting"|"signing-in"|"ready"|"reconnecting"|"error"|"signed-out"} state
     * @param {{ alert?: boolean }} [options]
     */
    #setLoginStatus(text, state, options = {}) {
        const { statusEl } = this.form;
        if (!statusEl) return;

        const trimmed = (text || "").trim();
        statusEl.hidden = state === "idle" && !trimmed;
        statusEl.textContent = trimmed;
        statusEl.dataset.state = state;

        if (options.alert || state === "error") {
            statusEl.setAttribute("role", "alert");
            statusEl.setAttribute("aria-live", "assertive");
        } else {
            statusEl.setAttribute("role", "status");
            statusEl.setAttribute("aria-live", "polite");
        }
    }

    #setAuthPending(pending, label) {
        const { joinButton, usernameInput, passwordInput } = this.form;
        joinButton.disabled = pending;
        usernameInput.disabled = pending;
        passwordInput.disabled = pending;
        joinButton.textContent = pending
            ? (label || "Signing in…")
            : this.joinButtonDefaultLabel;
        joinButton.dataset.loading = pending ? "true" : "false";
    }

    #setChatEnabled(enabled) {
        if (!this.chat) return;
        this.chat.inputEl.disabled = !enabled;
        this.chat.sendBtn.disabled = !enabled;
    }

    #setMicError(message) {
        if (!this.micErrorEl) return;
        const text = (message || "").trim();
        this.micErrorEl.hidden = !text;
        this.micErrorEl.textContent = text;
    }

    /**
     * Surface a non-fatal playback-decoder failure without blocking the dashboard.
     * @param {string} message
     */
    setDecoderError(message) {
        this.#setMicError(message);
    }

    #setGroupsError(message, showRetry) {
        if (!this.groupsErrorEl) return;
        const text = (message || "").trim();
        this.groupsErrorEl.hidden = !text;
        this.groupsErrorEl.replaceChildren();
        if (!text) return;

        const span = document.createElement("span");
        span.textContent = text;
        this.groupsErrorEl.appendChild(span);

        if (showRetry) {
            const retry = document.createElement("button");
            retry.type = "button";
            retry.className = "btn btn-ghost";
            retry.textContent = "Retry";
            retry.addEventListener("click", () => {
                this.#setGroupsError("", false);
                this.webSocketController.subscribeGroups();
                console.debug("SVG groups: subscription requested");
            });
            this.groupsErrorEl.appendChild(retry);
        }
    }

    #applySessionModeControls(mode) {
        const isNative = mode === "NATIVE_VOICE_CONTROLLER";
        const { audioModeEl, nativeNoticeEl, voiceControlsEl } = this.dashboard;
        if (audioModeEl) {
            audioModeEl.textContent = mode || "—";
        }
        if (nativeNoticeEl) {
            nativeNoticeEl.hidden = !isNative;
        }
        if (voiceControlsEl) {
            voiceControlsEl.hidden = isNative;
        }
        if (isNative) {
            this.audioController.stopMic();
            this.#setMicError("");
            this.#setVoiceTransmitEnabled(false);
        } else {
            this.#syncVoicePrivacyUi();
        }
    }

    /**
     * Client privacy gate: mute TX controls until confirmed group membership.
     * @param {boolean} enabled
     */
    #setVoiceTransmitEnabled(enabled) {
        this.voiceTransmitEnabled = !!enabled;
        this.webSocketController.setInGroup(this.voiceTransmitEnabled);

        const { muteBtn } = this.audio;
        const transmitModeSelect = this.ptt?.transmitModeSelect;
        const pushToTalkBtn = this.ptt?.pushToTalkBtn;
        const fullscreenPttBtn = this.ptt?.fullscreenPttBtn;

        if (muteBtn) muteBtn.disabled = !this.voiceTransmitEnabled;
        if (transmitModeSelect) transmitModeSelect.disabled = !this.voiceTransmitEnabled;
        if (pushToTalkBtn) pushToTalkBtn.disabled = !this.voiceTransmitEnabled;
        if (fullscreenPttBtn) fullscreenPttBtn.disabled = !this.voiceTransmitEnabled;

        if (!this.voiceTransmitEnabled) {
            // Prefer logically muted until membership is confirmed.
            if (!this.audioController.muted) {
                this.audioController.toggleMute();
                this.privacyMuted = true;
                if (muteBtn) {
                    muteBtn.classList.remove("unmuted");
                    muteBtn.classList.add("muted");
                    muteBtn.textContent = "Unmute";
                }
            }
            if (this.audio?.micIndicator) {
                this.audio.micIndicator.classList.remove("active");
            }
        } else if (this.privacyMuted && this.audioController.muted) {
            this.audioController.toggleMute();
            this.privacyMuted = false;
            if (muteBtn) {
                muteBtn.classList.remove("muted");
                muteBtn.classList.add("unmuted");
                muteBtn.textContent = "Mute";
            }
        }

        this.#setVoiceGateMessage(
            this.voiceTransmitEnabled
                ? ""
                : "Voice will not transmit until you join a voice group."
        );
        this.#syncVoiceStatusChips();
    }

    #syncVoiceStatusChips() {
        const connEl = this.dashboard.voiceConnEl;
        const groupEl = this.dashboard.voiceGroupEl;
        const wsStatus = this.dashboard.wsStatusEl?.textContent || "Signed out";
        if (connEl) {
            connEl.textContent = wsStatus;
        }
        const current = this.groups.currentGroupEl?.textContent || "None";
        if (groupEl) {
            groupEl.textContent = current;
        }
    }

    #setVoiceGateMessage(message) {
        if (!this.voiceGateEl) {
            const voiceControls = this.dashboard.voiceControlsEl;
            if (voiceControls) {
                this.voiceGateEl = voiceControls.querySelector("[data-svg='dash.voice-gate']");
            }
        }
        if (!this.voiceGateEl) return;
        const text = (message || "").trim();
        this.voiceGateEl.hidden = !text;
        this.voiceGateEl.textContent = text;
    }

    #syncVoicePrivacyUi() {
        if (!this.appState?.isReady() || !this.appState.isWebVoice()) {
            this.#setVoiceTransmitEnabled(false);
            return;
        }
        const inGroup = !!this.groupsController?.isInGroup();
        this.#setVoiceTransmitEnabled(inGroup);
    }

    /**
     * Enter dashboard after authoritative ready. Never gated on microphone init.
     * @param {string} playerName
     */
    #enterDashboard(playerName) {
        const { playerNameEl, wsStatusEl } = this.dashboard;
        if (playerNameEl) playerNameEl.textContent = playerName || "";
        if (wsStatusEl) {
            wsStatusEl.textContent = "Connected";
            wsStatusEl.dataset.state = "connected";
        }
        this.#syncVoiceStatusChips();

        this.#setAuthPending(false);
        this.#setLoginStatus("", "idle");
        this.#setChatEnabled(true);
        this.groupsController?.setAllowWebCreation(
            this.webSocketController.getAllowWebCreation()
        );
        if (this.#isBuildMatched()) {
            this.groupsController?.setEnabled(true);
        } else {
            this.groupsController?.setEnabled(false);
            Logger.log("[SVG Build] Group mutations blocked until frontend/server builds match.");
        }

        const mode = this.appState?.facts.sessionMode
            || this.webSocketController.getSessionMode();
        if (mode) {
            this.#applySessionModeControls(mode);
        }

        // Flush any snapshot that arrived before the dashboard was shown.
        const pending = this.appState?.takePendingGroupSnapshot();
        if (pending) {
            this.groupsController?.applySnapshot(pending);
            const rev = Number(pending.revision);
            const count = Array.isArray(pending.groups) ? pending.groups.length : 0;
            console.debug(
                `SVG groups: snapshot revision=${Number.isFinite(rev) ? rev : "?"} count=${count}`
            );
        }

        // Confirm subscription (server may already have pushed a snapshot).
        this.webSocketController.subscribeGroups();
        console.debug("SVG groups: subscription requested");

        // Mic may initialize for device access, but TX stays gated until group membership.
        void this.#maybeStartMic().finally(() => {
            this.micGraphGenerationAtLayout = this.audioController.getActiveMicGeneration?.() || 0;
        });
        this.#syncVoicePrivacyUi();
        this.#syncVoiceStatusChips();
    }

    #initDashboardLayout() {
        const gridEl = this.dashboard.gridEl;
        const voiceEl = this.dashboard.voiceControlsEl;
        const chatEl = this.chat?.logEl?.closest?.("[data-panel='chat']")
            || document.querySelector("[data-panel='chat']");
        const appearanceEl = document.querySelector("[data-panel='appearance']");
        const groupsEl = this.groups.listEl?.closest?.("[data-panel='groups']")
            || document.querySelector("[data-panel='groups']");

        // Appearance must initialize even if layout panels are incomplete.
        this.#initAppearance();

        if (!gridEl || !voiceEl || !chatEl || !appearanceEl || !groupsEl) {
            return;
        }

        this.micGraphGenerationAtLayout = this.audioController.getActiveMicGeneration?.() || 0;
        const customLayoutEnabled = typeof window !== "undefined"
            && window.SVG_CUSTOM_LAYOUT_ENABLED === true;
        this.layoutController = new DashboardLayoutController({
            gridEl,
            panels: {
                voice: voiceEl,
                chat: chatEl,
                appearance: appearanceEl,
                groups: groupsEl
            },
            resetBtn: this.dashboard.resetLayoutBtn || null,
            liveRegion: this.dashboard.layoutLiveEl || null,
            customLayoutEnabled,
            onLayoutChange: () => {
                // Panel moves must never recreate live audio/WebSocket objects.
                const gen = this.audioController.getActiveMicGeneration?.() || 0;
                if (this.micGraphGenerationAtLayout && gen !== this.micGraphGenerationAtLayout) {
                    console.warn("SVG layout: microphone generation changed unexpectedly during layout move");
                }
                this.micGraphGenerationAtLayout = gen;
                this.#syncVoiceStatusChips();
            }
        });
        this.layoutController.init();
    }

    async #maybeStartMic() {
        if (!this.appState?.isReady()) return;
        if (!this.appState.isWebVoice()) return;
        if (this.appState.facts.sessionMode === "NATIVE_VOICE_CONTROLLER") return;
        if (!this.#isBuildMatched()) {
            Logger.log("[SVG Build] Microphone blocked until frontend/server builds match.");
            this.#setMicError("Client update required — reload to match the server build.");
            return;
        }

        const runtime = this.audioController.getAudioRuntime();
        if (!runtime.canCaptureMic) {
            this.#setMicError(
                "Microphone capture is unavailable in this browser. Groups and chat still work."
            );
            Logger.log(
                "[Audio] Mic capture unsupported in this browser/context. Joined in compatibility mode."
            );
            return;
        }

        try {
            await this.audioController.startMic(this.audio.micSelect.value);
            this.#setMicError("");
            // Keep logically muted until a group is joined.
            if (!this.groupsController?.isInGroup() && !this.audioController.muted) {
                this.audioController.toggleMute();
                if (this.audio.muteBtn) {
                    this.audio.muteBtn.classList.remove("unmuted");
                    this.audio.muteBtn.classList.add("muted");
                    this.audio.muteBtn.textContent = "Unmute";
                }
            }
            this.#syncVoicePrivacyUi();
            this.#populateBuildDiagnostics({
                serverBuildId: this.serverBuildId,
                workletBuildId: this.audioController.workletBuildId,
                decoderBuildId: window.DECODER_BUILD_ID
            });
        } catch (error) {
            console.error(error);
            this.#setMicError(
                "Could not start the microphone. Check permissions — groups and chat are still available."
            );
            Logger.log("Failed to start microphone. Receive/chat still available.");
        }
    }

    #onAppStateChange(_from, to) {
        const { wsStatusEl } = this.dashboard;

        if (to === AppState.READY_WEB_VOICE || to === AppState.READY_NATIVE_CONTROLLER) {
            this.#enterDashboard(this.appState.facts.playerName);
            this.#applySessionModeControls(this.appState.facts.sessionMode);
            return;
        }

        if (to === AppState.RECONNECTING) {
            this.audioController.stopMic();
            this.#setChatEnabled(false);
            this.groupsController?.setEnabled(false);
            if (wsStatusEl) {
                wsStatusEl.textContent = "Reconnecting…";
                wsStatusEl.dataset.state = "reconnecting";
            }
            this.#syncVoiceStatusChips();
            this.#setLoginStatus("Reconnecting…", "reconnecting");
            return;
        }

        if (to === AppState.CONNECTING) {
            this.#setLoginStatus("Connecting…", "connecting");
            return;
        }

        if (to === AppState.AUTHENTICATING) {
            this.#setLoginStatus("Signing in…", "signing-in");
            this.#setAuthPending(true, "Signing in…");
            return;
        }

        if (to === AppState.ERROR) {
            this.audioController.stopMic();
            this.pttController?.reset();
            this.#setChatEnabled(false);
            this.groupsController?.reset();
            this.#setAuthPending(false);
            if (wsStatusEl) {
                wsStatusEl.textContent = "Connection lost";
                wsStatusEl.dataset.state = "disconnected";
            }
            return;
        }

        if (to === AppState.LOGGED_OUT) {
            this.audioController.stopMic();
            this.pttController?.reset();
            this.#setChatEnabled(false);
            this.groupsController?.reset();
            this.#setAuthPending(false);
            this.#setMicError("");
            this.#setGroupsError("", false);
            if (wsStatusEl) {
                wsStatusEl.textContent = "Signed out";
                wsStatusEl.dataset.state = "idle";
            }
        }
    }

    async init() {
        const {
            formEl,
            joinButton,
            statusEl,
            usernameInput,
            passwordInput,
            passwordToggle,
            copyPswdBtn
        } = this.form;

        const {
            speakerSelect,
            micSelect,
            muteBtn,
            micIndicator
        } = this.audio;

        const {
            micCard,
            transmitModeSelect,
            pttCard,
            pttBindingControls,
            bindPttBtn,
            clearPttBtn,
            pttBindingLabel,
            pttControls,
            pushToTalkBtn,
            fullscreenPttBtn,
            pttFullscreenOverlay,
            pushToTalkFullscreenBtn,
            exitFullscreenPttBtn,
            allowBackgroundPttCheckbox
        } = this.ptt;

        const { devToggle, devContent } = this.dev;

        this.joinButtonDefaultLabel = joinButton.textContent || "Log In";
        this.micErrorEl = this.dashboard.micErrorEl || null;
        this.groupsErrorEl = this.groups.errorEl || null;

        if (this.audio.levelMeterEl) {
            this.audioController.setLevelMeter(this.audio.levelMeterEl);
        }

        this.#initDashboardLayout();
        this.#wireBuildMismatchControls();

        this.appState = new AppStateController(
            {
                loginView: this.views.loginView,
                dashboardView: this.views.dashboardView,
                reconnectOverlay: this.dashboard.reconnectOverlay || null
            },
            {
                onStateChange: (from, to) => this.#onAppStateChange(from, to)
            }
        );

        // Deterministic logged-out start — no red failure banner.
        this.#setLoginStatus("", "idle");
        this.#setChatEnabled(false);
        this.#setMicError("");
        this.#setGroupsError("", false);

        if (passwordToggle) {
            passwordToggle.addEventListener("click", () => {
                const showing = passwordInput.type === "text";
                passwordInput.type = showing ? "password" : "text";
                passwordToggle.setAttribute("aria-pressed", showing ? "false" : "true");
                passwordToggle.textContent = showing ? "Show" : "Hide";
                passwordToggle.setAttribute("aria-label", showing ? "Show password" : "Hide password");
            });
        }

        // Copy command is bound via document-level delegation in index.html
        // (handleCopyCommand) so the listener always survives builder wiring.
        void copyPswdBtn;

        if (devContent) {
            devContent.classList.add("dev-hidden");
        }

        if (devToggle && devContent) {
            devToggle.addEventListener("click", () => {
                const isHidden = devContent.classList.toggle("dev-hidden");
                devToggle.textContent = !isHidden
                    ? "Developer Tools ▲"
                    : "Developer Tools ▼";
            });
        }

        this.audioController.setMicIndicator(micIndicator);

        // Device enumeration must never block UI startup.
        void this.populateAudioDevices()
            .then(() => Logger.log("Audio devices loaded successfully."))
            .catch((error) => {
                console.error(error);
                Logger.log("Failed to load audio devices.");
            });

        if (navigator.mediaDevices && typeof navigator.mediaDevices.addEventListener === "function") {
            navigator.mediaDevices.addEventListener("devicechange", () => {
                void this.populateAudioDevices()
                    .then(() => Logger.log("Audio device list refreshed."))
                    .catch((error) => {
                        console.error(error);
                        Logger.log("Failed to refresh audio devices.");
                    });
            });
        }

        this.pttController = new PttController(/** @type {PttElements} */ {
            micCard,
            transmitModeSelect,
            pttCard,
            pttBindingControls,
            bindPttBtn,
            clearPttBtn,
            pttBindingLabel,
            pttControls,
            pushToTalkBtn,
            fullscreenPttBtn,
            pttFullscreenOverlay,
            pushToTalkFullscreenBtn,
            exitFullscreenPttBtn,
            allowBackgroundPttCheckbox
        });

        this.pttController.init();

        this.audioController.setTransmitModeProvider(
            () => (this.pttController.isPttMode() ? "ptt" : "voice")
        );

        this.audioController.setPttActiveProvider(
            () => this.pttController.isPttActive()
        );

        this.groupsController = new GroupsController({
            webSocketController: this.webSocketController,
            appState: this.appState,
            onSnapshot: (revision, count) => {
                console.debug(`groups: snapshot revision=${revision} count=${count}`);
                this.#setGroupsError("", false);
                this.#syncVoicePrivacyUi();
                this.#syncVoiceStatusChips();
            },
            onMembershipChange: (groupId) => {
                console.debug(`groups: membership group=${groupId || "none"}`);
                this.#syncVoicePrivacyUi();
                this.#syncVoiceStatusChips();
            },
            onLoadError: (message) => {
                this.#setGroupsError(
                    message || "Could not load voice groups.",
                    true
                );
            },
            ...this.groups
        });
        this.groupsController.init();
        this.#syncVoiceStatusChips();

        this.webSocketController.onSessionMode((mode) => {
            this.appState.updateFacts({ sessionMode: mode });
            if (this.appState.isDashboardVisible()) {
                this.#applySessionModeControls(mode);
            }
            if (this.appState.isReady() && mode === "WEB_VOICE") {
                void this.#maybeStartMic();
            }
        });

        this.webSocketController.onAuthenticated(() => {
            this.appState.updateFacts({ authenticated: true });
            this.appState.beginAuthenticating();
        });

        this.webSocketController.onBuildIdentity?.((identity) => {
            this.serverBuildId = identity?.serverBuildId || null;
            if (typeof identity?.customLayoutEnabled === "boolean") {
                window.SVG_CUSTOM_LAYOUT_ENABLED = identity.customLayoutEnabled;
                this.layoutController?.setCustomLayoutEnabled?.(identity.customLayoutEnabled);
            }
            this.#populateBuildDiagnostics({
                serverBuildId: identity?.serverBuildId,
                protocolVersion: identity?.protocolVersion,
                workletBuildId: this.audioController?.workletBuildId,
                decoderBuildId: window.DECODER_BUILD_ID
            });
        });

        this.audioController.onWorkletBuild = (buildId) => {
            this.#populateBuildDiagnostics({
                serverBuildId: this.serverBuildId,
                workletBuildId: buildId,
                decoderBuildId: window.DECODER_BUILD_ID
            });
        };

        this.dashboard.logoutBtn?.addEventListener("click", () => {
            this.webSocketController.disconnect();
            passwordInput.value = "";
            this.appState.logout();
            this.#setLoginStatus("Signed out", "signed-out");
        });

        formEl.addEventListener("submit", (e) => {
            e.preventDefault();

            if (this.appState.getState() === AppState.CONNECTING
                || this.appState.getState() === AppState.AUTHENTICATING) {
                return;
            }

            if (this.webSocketController.isConnected()) {
                this.webSocketController.disconnect();
                passwordInput.value = "";
                this.appState.logout();
                this.#setLoginStatus("Signed out", "signed-out");
                return;
            }

            const username = usernameInput.value.trim();
            const password = passwordInput.value;

            if (!username || !password) {
                this.#setLoginStatus(
                    "Username and password are required.",
                    "error",
                    { alert: true }
                );
                return;
            }

            this.appState.beginConnecting();
            this.#setAuthPending(true, "Connecting…");
            this.#setLoginStatus("Connecting…", "connecting");

            this.webSocketController.connect(
                username,
                password,
                (connected, readyUsername, phase) => {
                    if (connected) {
                        // Password cleared from the input; reconnect memory stays in the socket controller.
                        passwordInput.value = "";
                        this.appState.updateFacts({
                            authenticated: true,
                            ready: true,
                            playerName: readyUsername || username,
                            sessionMode: this.webSocketController.getSessionMode()
                        });
                        return;
                    }

                    if (phase === "connecting") {
                        this.appState.beginConnecting();
                        this.#setLoginStatus("Connecting…", "connecting");
                        return;
                    }

                    if (phase === "authenticating") {
                        this.appState.beginAuthenticating();
                        return;
                    }

                    if (phase === "reconnecting") {
                        this.appState.beginReconnecting();
                        return;
                    }

                    if (phase === "auth_failed") {
                        passwordInput.value = "";
                        this.appState.failAuth();
                        this.#setLoginStatus(
                            "Invalid credentials. Check username and SVG password.",
                            "error",
                            { alert: true }
                        );
                        this.#setAuthPending(false);
                        return;
                    }

                    // Unexpected disconnect / closed after a session (or pre-login network drop).
                    const hadSession = this.appState.isDashboardVisible()
                        || this.appState.getState() === AppState.RECONNECTING
                        || this.appState.facts.ready;
                    passwordInput.value = "";
                    this.#setAuthPending(false);
                    if (hadSession) {
                        this.appState.connectionLost({ willReconnect: false });
                        this.appState.logout();
                        this.#setLoginStatus("Connection lost", "error", { alert: true });
                    } else {
                        // Keep an existing auth error message if present.
                        const current = (this.form.statusEl?.textContent || "").trim();
                        const keepAuthError = this.form.statusEl?.dataset.state === "error"
                            && current.toLowerCase().includes("credential");
                        this.appState.logout();
                        if (!keepAuthError) {
                            this.#setLoginStatus("", "idle");
                        }
                    }
                }
            );
        });

        muteBtn.addEventListener("click", () => {
            const muted = this.audioController.toggleMute();
            this.pttController.setMuted(muted);
            muteBtn.textContent = muted ? "Unmute" : "Mute";
            muteBtn.classList.toggle("muted", muted);
            muteBtn.classList.toggle("unmuted", !muted);
        });

        speakerSelect.addEventListener("change", async () => {
            localStorage.setItem("preferredSpeaker", speakerSelect.value);
            await this.audioController.setOutputDevice(speakerSelect.value);
        });

        micSelect.addEventListener("change", async () => {
            localStorage.setItem("preferredMic", micSelect.value);

            if (this.appState?.isReady() && this.appState.isWebVoice()) {
                const runtime = this.audioController.getAudioRuntime();
                if (!runtime.canCaptureMic) {
                    Logger.debug("[Audio] Mic capture unavailable, cannot switch microphone.");
                    return;
                }
                try {
                    await this.audioController.rebuildMicPipeline(micSelect.value);
                    this.#setMicError("");
                    this.micGraphGenerationAtLayout = this.audioController.getActiveMicGeneration?.() || 0;
                } catch (error) {
                    console.error(error);
                    this.#setMicError("Could not switch microphone.");
                }
            }
        });

        this.#bindMicProcessingControls();

        const runtime =
            this.audioRuntime ||
            this.audioController.getAudioRuntime();

        if (runtime?.degradedReason) {
            Logger.log(`[Audio] ${runtime.degradedReason}`);
        }

        // Silence unused binding warning for statusEl when idle-hidden.
        void statusEl;
    }

    #bindMicProcessingControls() {
        const prefs = this.audioController.getProcessingPrefs?.() || {
            echoCancellation: true,
            noiseSuppression: true,
            autoGainControl: true
        };
        const echoEl = this.audio.echoCancelEl;
        const noiseEl = this.audio.noiseSuppressEl;
        const agcEl = this.audio.agcEl;
        const resetBtn = this.audio.resetAudioSettingsBtn;

        if (echoEl) echoEl.checked = !!prefs.echoCancellation;
        if (noiseEl) noiseEl.checked = !!prefs.noiseSuppression;
        if (agcEl) agcEl.checked = !!prefs.autoGainControl;

        const apply = async () => {
            if (!this.audioController.setProcessingPrefs) return;
            await this.audioController.setProcessingPrefs({
                echoCancellation: echoEl ? !!echoEl.checked : true,
                noiseSuppression: noiseEl ? !!noiseEl.checked : true,
                autoGainControl: agcEl ? !!agcEl.checked : true
            });
            this.micGraphGenerationAtLayout = this.audioController.getActiveMicGeneration?.() || 0;
            this.#syncAppliedMicStatus();
        };

        echoEl?.addEventListener("change", () => { void apply(); });
        noiseEl?.addEventListener("change", () => { void apply(); });
        agcEl?.addEventListener("change", () => { void apply(); });
        resetBtn?.addEventListener("click", async (event) => {
            event.preventDefault();
            await this.audioController.resetProcessingPrefs?.();
            const next = this.audioController.getProcessingPrefs?.() || prefs;
            if (echoEl) echoEl.checked = !!next.echoCancellation;
            if (noiseEl) noiseEl.checked = !!next.noiseSuppression;
            if (agcEl) agcEl.checked = !!next.autoGainControl;
            this.micGraphGenerationAtLayout = this.audioController.getActiveMicGeneration?.() || 0;
            this.#syncAppliedMicStatus();
        });

        this.audioController.onAppliedSettingsChange = () => this.#syncAppliedMicStatus();
        this.#syncAppliedMicStatus();
    }

    #syncAppliedMicStatus() {
        const el = this.audio?.appliedStatusEl;
        if (!el || !this.audioController?.getAppliedMicStatus) {
            return;
        }
        const status = this.audioController.getAppliedMicStatus();
        const applied = status.applied;
        const support = status.support || {};
        const echoEl = this.audio.echoCancelEl;
        const noiseEl = this.audio.noiseSuppressEl;
        const agcEl = this.audio.agcEl;

        const mark = (input, supported) => {
            if (!input) return;
            const row = input.closest(".svg-switch");
            if (!supported) {
                input.disabled = true;
                if (row) {
                    row.classList.add("is-unsupported");
                    const label = row.querySelector(".svg-switch-label");
                    if (label && !label.dataset.baseLabel) {
                        label.dataset.baseLabel = label.textContent || "";
                        label.textContent = `${label.dataset.baseLabel} (Not supported)`;
                    }
                }
            } else {
                input.disabled = false;
                row?.classList.remove("is-unsupported");
            }
        };

        mark(echoEl, support.echoCancellation !== false);
        mark(noiseEl, support.noiseSuppression !== false);
        mark(agcEl, support.autoGainControl !== false);

        if (!applied) {
            el.textContent = "Applied settings: waiting for microphone…";
            return;
        }
        const fmt = (v) => (v === true ? "on" : v === false ? "off" : "n/a");
        el.textContent =
            `Applied: echo ${fmt(applied.echoCancellation)}, ` +
            `noise ${fmt(applied.noiseSuppression)}, ` +
            `AGC ${fmt(applied.autoGainControl)}` +
            (applied.sampleRate ? ` · ${applied.sampleRate} Hz` : "") +
            (applied.channelCount ? ` · ${applied.channelCount} ch` : "");
    }
}
