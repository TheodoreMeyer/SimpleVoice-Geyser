import {SvgClient} from "../client.js";

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

        screens: {
            root: "ui.screens",
            openSettings: "ui.open-settings",
            openGroups: "ui.open-groups",
            openCreateGroup: "ui.open-create-group",
            hubVolumesIcon: "ui.hub-volumes-icon",
            openMic: "ui.open-mic",
            openSpeaker: "ui.open-speaker",
            joinOpenMic: "ui.join-open-mic",
            joinOpenSpeaker: "ui.join-open-speaker",
            groupsList: "ui.groups-list",
            groupsOpenCurrent: "ui.groups-open-current",
            groupsPassword: "ui.groups-password",
            groupsJoinSubmit: "ui.groups-join-submit",
            groupsLeave: "ui.groups-leave",
            groupMenuTitle: "ui.group-menu-title",
            groupMenuMembers: "ui.group-menu-members",
            createGroupName: "ui.create-group-name",
            createGroupPassword: "ui.create-group-password",
            createGroupType: "ui.create-group-type",
            createGroupTypeHint: "ui.create-group-type-hint",
            createGroupSubmit: "ui.create-group-submit"
        },

        audio: {
            speakerSelect: "audio.speaker",
            micSelect: "audio.mic",
            muteBtn: "audio.mute",
            muteSpeakerBtn: { optional: "audio.mute-speaker" },
            micIndicator: "audio.indicator",
            micSettings: "audio.mic-settings",
            vadMeter: "audio.vad-meter",
            vadEnergyBar: "audio.vad-energy",
            vadMarker: "audio.vad-marker",
            vadSlider: "audio.vad-slider",
            gainSlider: "audio.gain-slider",
            gainValueLabel: "audio.gain-value",
            masterSlider: "audio.master-slider",
            masterValueLabel: "audio.master-value",
            volumesRoot: "audio.volumes",
            volumesSearch: "audio.volumes-search",
            volumesList: "audio.volumes-list",
            micList: "audio.mic-list",
            speakerList: "audio.speaker-list"
        },

        ptt: {
            micCard: "ptt.mic-card",
            transmitModeSelect: "ptt.mode",
            activationMethodBtn: { optional: "ptt.activation-btn" },

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

        root.querySelectorAll("[data-svg]").forEach(element => {
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
            } else if (value && typeof value === "object" && typeof value.optional === "string") {
                result[key] = this.elements[value.optional] || null;
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