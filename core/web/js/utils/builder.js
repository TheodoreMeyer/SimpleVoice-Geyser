import { SvgClient } from "../client.js";

/**
 * Maps SvgClientOptions to HTML data-svg attributes.
 */
const ELEMENTS = {
    ui: {
        form: {
            formEl: "form",
            joinButton: "form.join",
            statusEl: "form.status",
            usernameInput: "form.username",
            passwordInput: "form.password",
            passwordToggle: "form.password-toggle",
            copyPswdBtn: "form.copy-pswd",
            copyStatusEl: "form.copy-status"
        },

        audio: {
            speakerSelect: "audio.speaker",
            micSelect: "audio.mic",
            muteBtn: "audio.mute",
            micIndicator: "audio.indicator",
            levelMeterEl: "audio.level",
            echoCancelEl: "audio.echo",
            noiseSuppressEl: "audio.noise",
            agcEl: "audio.agc",
            resetAudioSettingsBtn: "audio.reset-settings",
            appliedStatusEl: "audio.applied-status"
        },

        ptt: {
            micCard: "ptt.mic-card",
            transmitModeSelect: "ptt.mode",

            pttCard: "ptt.card",
            pttBindingControls: "ptt.binding-controls",

            bindPttBtn: "ptt.bind",
            clearPttBtn: "ptt.clear",

            pttBindingLabel: "ptt.binding-label",

            pttControls: "ptt.controls",

            pushToTalkBtn: "ptt.button",
            fullscreenPttBtn: "ptt.fullscreen",

            pttFullscreenOverlay: "ptt.overlay",

            pushToTalkFullscreenBtn: "ptt.overlay-button",
            exitFullscreenPttBtn: "ptt.overlay-exit",

            allowBackgroundPttCheckbox: "ptt.allow-background"
        },

        dev: {
            devToggle: "dev.toggle",
            devContent: "dev.content"
        },

        views: {
            loginView: "view.login",
            dashboardView: "view.dashboard"
        },

        dashboard: {
            playerNameEl: "dash.player",
            wsStatusEl: "dash.ws-status",
            audioModeEl: "dash.audio-mode",
            nativeNoticeEl: "dash.native-notice",
            voiceControlsEl: "dash.voice-controls",
            logoutBtn: "dash.logout",
            resetLayoutBtn: "dash.reset-layout",
            resetAppearanceBtn: "dash.reset-appearance",
            appearanceAccentGroup: "dash.appearance-accent",
            appearanceBorderGroup: "dash.appearance-border",
            appearanceAccentSwatches: "dash.appearance-accent-swatches",
            appearanceBorderSwatches: "dash.appearance-border-swatches",
            layoutLiveEl: "dash.layout-live",
            micErrorEl: "dash.mic-error",
            reconnectOverlay: "dash.reconnect-overlay",
            gridEl: "dash.grid",
            voiceConnEl: "dash.voice-conn",
            voiceGroupEl: "dash.voice-group",
            voiceGateEl: "dash.voice-gate"
        },

        groups: {
            listEl: "groups.list",
            currentGroupEl: "groups.current",
            createBtn: "groups.create",
            leaveBtn: "groups.leave",
            refreshBtn: "groups.refresh",
            refreshStatusEl: "groups.refresh-status",
            createModal: "groups.create-modal",
            createForm: "groups.create-form",
            createNameInput: "groups.create-name",
            createPasswordInput: "groups.create-password",
            createTypeSelect: "groups.create-type",
            createTypeHelp: "groups.create-type-help",
            createErrorEl: "groups.create-error",
            createCloseBtn: "groups.create-close",
            createCancelBtn: "groups.create-cancel",
            createSubmitBtn: "groups.create-submit",
            joinModal: "groups.join-modal",
            joinForm: "groups.join-form",
            joinPasswordInput: "groups.join-password",
            joinGroupNameEl: "groups.join-name",
            joinErrorEl: "groups.join-error",
            joinCloseBtn: "groups.join-close",
            joinCancelBtn: "groups.join-cancel",
            typeHintEl: "groups.type-hint",
            errorEl: "groups.error"
        },

        chat: {
            logEl: "chat.log",
            inputEl: "chat.input",
            sendBtn: "chat.send"
        }
    },

    chat: {
        logEl: "chat.log",
        inputEl: "chat.input",
        sendBtn: "chat.send"
    }
};

export class SvgBuilder {

    /**
     * Creates a builder using the entire document.
     *
     * @returns {SvgBuilder}
     */
    static fromDocument() {
        return new SvgBuilder(document);
    }

    /**
     * Directly creates the client from the options given
     * @param {SvgClientOptions} options
     * @returns {SvgClient}
     */
    static fromElements(options) {
        return new SvgClient(options);
    }

    /**
     * @param {ParentNode} root
     */
    constructor(root) {
        this.root = root;

        this.elements = {};

        root.querySelectorAll("[data-svg]").forEach((element) => {
            const id = element.dataset.svg;

            if (this.elements[id]) {
                throw new Error(`Duplicate data-svg="${id}"`);
            }

            this.elements[id] = element;
        });
    }

    /**
     * Builds a SvgClient using the configured HTML.
     *
     * @returns {SvgClient}
     */
    build() {
        return new SvgClient(this.#resolveObject(ELEMENTS));
    }

    /**
     * Recursively replaces every string in the mapping
     * with its corresponding DOM element.
     *
     * @param {Object} object
     * @returns {Object}
     */
    #resolveObject(object) {
        const result = {};

        for (const [key, value] of Object.entries(object)) {
            if (typeof value === "string") {
                result[key] = this.#require(value);
            } else {
                result[key] = this.#resolveObject(value);
            }
        }

        return result;
    }

    /**
     * Finds an element by data-svg.
     *
     * @param {string} id
     * @returns {HTMLElement}
     */
    #require(id) {
        const element = this.elements[id];

        if (!element) {
            throw new Error(
                `Missing required element: data-svg="${id}"`
            );
        }

        return element;
    }
}
