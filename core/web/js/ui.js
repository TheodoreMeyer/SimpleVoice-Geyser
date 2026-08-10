import {Logger} from "./utils/logger.js";
import {SvgLang} from "./utils/lang.js";
import {PttController} from "./ptt.js";

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

        this.pttController = null;
    }

    setSelectUnavailable(select, label) {
        select.innerHTML = "";

        const option = document.createElement("option");
        option.disabled = true;
        option.selected = true;
        SvgLang.setElement(option, label);

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
                reason || SvgLang.string("audioFallbackDefaultReason");

            this.setSelectUnavailable(
                micSelect,
                "microphoneUnavailableLabel"
            );

            this.setSelectUnavailable(
                speakerSelect,
                "speakerUnavailableLabel"
            );

            Logger.log(`[Audio] ${fallbackReason}`);
            return;
        }

        for (const mic of microphones) {
            const option = document.createElement("option");

            option.value = mic.deviceId;
            SvgLang.setElement(option,
                () => mic.label ||
                    `${SvgLang.string("microphoneIndexPrefix")} ${micSelect.options.length + 1}`
            );

            micSelect.appendChild(option);
        }

        for (const speaker of speakers) {
            const option = document.createElement("option");

            option.value = speaker.deviceId;
            SvgLang.setElement(option,
                () => speaker.label ||
                    `${SvgLang.string("speakerIndexPrefix")} ${speakerSelect.options.length + 1}`
            );

            speakerSelect.appendChild(option);
        }

        if (microphones.length === 0) {
            this.setSelectUnavailable(
                micSelect,
                "noMicrophoneDetectedLabel"
            );
        } else {
            micSelect.disabled = false;
        }

        if (speakers.length === 0) {
            this.setSelectUnavailable(
                speakerSelect,
                "noSpeakerDetectedLabel"
            );
        } else {
            speakerSelect.disabled = false;
        }

        const savedMic = localStorage.getItem("preferredMic");
        const savedSpeaker = localStorage.getItem("preferredSpeaker");

        if (savedMic &&
            microphones.some(d => d.deviceId === savedMic)
        ) {
            micSelect.value = savedMic;
        }

        if (savedSpeaker &&
            speakers.some(d => d.deviceId === savedSpeaker)
        ) {
            speakerSelect.value = savedSpeaker;

            await this.audioController.setOutputDevice(savedSpeaker);
        }
    }

    async init() {

        const {
            formEl,
            joinButton,
            statusEl,
            usernameInput,
            passwordInput
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

        this.audioController.setMicIndicator(micIndicator);

        try {
            await this.populateAudioDevices();
            Logger.log("Audio devices loaded successfully.");
        } catch (error) {
            console.error(error);
            Logger.log("Failed to load audio devices.");
        }

        if (navigator.mediaDevices &&
            typeof navigator.mediaDevices.addEventListener === "function"
        ) {

            navigator.mediaDevices.addEventListener(
                "devicechange",
                async () => {

                    try {
                        await this.populateAudioDevices();

                        Logger.log(
                            "Audio device list refreshed."
                        );
                    } catch (error) {
                        console.error(error);

                        Logger.log(
                            "Failed to refresh audio devices."
                        );
                    }
                }
            );
        }

        this.pttController =
            new PttController( /** @type {PttElements} */{
                micCard: micCard,
                transmitModeSelect: transmitModeSelect,
                pttCard: pttCard,
                pttBindingControls: pttBindingControls,
                bindPttBtn: bindPttBtn,
                clearPttBtn: clearPttBtn,
                pttBindingLabel: pttBindingLabel,
                pttControls: pttControls,
                pushToTalkBtn: pushToTalkBtn,
                fullscreenPttBtn: fullscreenPttBtn,
                pttFullscreenOverlay: pttFullscreenOverlay,
                pushToTalkFullscreenBtn: pushToTalkFullscreenBtn,
                exitFullscreenPttBtn: exitFullscreenPttBtn,
                allowBackgroundPttCheckbox: allowBackgroundPttCheckbox
            });

        this.pttController.init();

        this.audioController.setTransmitModeProvider(
            () =>
                this.pttController.isPttMode()
                    ? "ptt"
                    : "voice"
        );

        this.audioController.setPttActiveProvider(
            () =>
                this.pttController.isPttActive()
        );

        formEl.addEventListener("submit", async e => {
                e.preventDefault();

                if (this.webSocketController.isConnected()) {
                    this.webSocketController.disconnect();
                    this.audioController.stopMic();
                    this.pttController.reset();
                    SvgLang.setElement(joinButton, "joinBtnUnconnectedLabel");
                    return;
                }

                this.webSocketController.connect(usernameInput.value,
                    passwordInput.value,

                    async (status) => {
                        if (status.connected) {

                            SvgLang.setElement(statusEl,
                                () => `${SvgLang.string("statusConnectedAsPrefix")} ${status.username}`
                            );
                            statusEl.classList.remove("disconnected");
                            statusEl.classList.add("connected");
                            SvgLang.setElement(joinButton, "joinBtnConnectedLabel");
                            micSelect.disabled = true;
                            speakerSelect.disabled = true;
                            const runtime = this.audioController.getAudioRuntime();

                            if (runtime.canCaptureMic) {

                                try {
                                    await this.audioController.startMic(micSelect.value);
                                } catch (error) {
                                    console.error(error);

                                    Logger.log(
                                        "Failed to start microphone. Receive/chat still available."
                                    );
                                }
                            } else {

                                Logger.log(
                                    "[Audio] Mic capture unsupported in this browser/context. Joined in compatibility mode."
                                );
                            }
                        } else {
                            SvgLang.setElement(statusEl, "statusDisconnectedLabel");
                            statusEl.classList.remove("connected");
                            statusEl.classList.add("disconnected");
                            micSelect.disabled = false;
                            speakerSelect.disabled = false;
                            this.pttController.reset();
                            SvgLang.setElement(joinButton, "joinBtnUnconnectedLabel");
                        }
                    }
                );
            }
        );

        muteBtn.addEventListener("click",
            () => {

                const muted = this.audioController.toggleMute();

                this.pttController.setMuted(muted);

                SvgLang.setElement(muteBtn,
                    () => muted
                        ? SvgLang.string("muteBtnUnmuteLabel")
                        : SvgLang.string("muteBtnMuteLabel")
                );

                muteBtn.classList.toggle("muted", muted);

                muteBtn.classList.toggle("unmuted", !muted);
            }
        );

        speakerSelect.addEventListener("change",
            async () => {

                localStorage.setItem(
                    "preferredSpeaker",
                    speakerSelect.value
                );

                await this.audioController.setOutputDevice(
                    speakerSelect.value
                );
            }
        );

        micSelect.addEventListener("change",
            async () => {

                localStorage.setItem("preferredMic", micSelect.value);

                if (this.webSocketController.isConnected()) {
                    const runtime = this.audioController.getAudioRuntime();

                    if (!runtime.canCaptureMic) {
                        Logger.debug(
                            "[Audio] Mic capture unavailable, cannot switch microphone."
                        );
                        return;
                    }

                    this.audioController.stopMic();

                    await this.audioController.startMic(
                        micSelect.value
                    );
                }
            }
        );

        const runtime =
            this.audioRuntime ||
            this.audioController.getAudioRuntime();

        if (runtime?.degradedReason) {
            Logger.log(
                `[Audio] ${runtime.degradedReason}`
            );
        }
    }
}