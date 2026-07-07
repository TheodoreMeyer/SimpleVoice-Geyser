import {SvgClient} from "./client.js";

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
            passwordInput: "form.password"
        },

        audio: {
            speakerSelect: "audio.speaker",
            micSelect: "audio.mic",
            muteBtn: "audio.mute",
            micIndicator: "audio.indicator"
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
     * @param {ParentNode} root
     */
    constructor(root) {
        this.root = root;
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
        const element = this.root.querySelector(`[data-svg="${id}"]`);

        if (!element) {
            throw new Error(
                `Missing required element: data-svg="${id}"`
            );
        }

        return element;
    }
}