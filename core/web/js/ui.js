import {Logger} from "./utils/logger.js";
import {PttController} from "./ptt.js";

/**
 * @import {SvgUIOptions} from "./utils/internal-types.js"
 * @import {PttElements} from "./utils/types.js"
 */

export class SvgUI {

    /** @type {import("./types.js").FormElements} */
    form;
    /** @type {import("./types.js").AudioElements} */
    audio;

    /** @type {import("./types.js").PttElements} */
    ptt;

    /** @type {import("./types.js").DevElements} */
    dev;


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

        this.pttController = null;
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

            this.setSelectUnavailable(
                micSelect,
                "Microphone unavailable"
            );

            this.setSelectUnavailable(
                speakerSelect,
                "Speaker unavailable"
            );

            Logger.log(`[Audio] ${fallbackReason}`);
            return;
        }

        for (const mic of microphones) {
            const option = document.createElement("option");

            option.value = mic.deviceId;
            option.textContent =
                mic.label ||
                `Microphone ${micSelect.options.length + 1}`;

            micSelect.appendChild(option);
        }

        for (const speaker of speakers) {
            const option = document.createElement("option");

            option.value = speaker.deviceId;
            option.textContent =
                speaker.label ||
                `Speaker ${speakerSelect.options.length + 1}`;

            speakerSelect.appendChild(option);
        }

        if (microphones.length === 0) {
            this.setSelectUnavailable(
                micSelect,
                "No microphones detected"
            );
        } else {
            micSelect.disabled = false;
        }

        if (speakers.length === 0) {
            this.setSelectUnavailable(
                speakerSelect,
                "No speakers detected"
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

        const {
            devToggle,
            devContent
        } = this.dev;

        devContent.classList.add("dev-hidden");

        devToggle.addEventListener("click",
            () => {

                const isHidden =
                    devContent.classList.toggle(
                        "dev-hidden"
                    );

                devToggle.textContent =
                    !isHidden
                        ? "Developer Tools ▲"
                        : "Developer Tools ▼";
            }
        );

        this.audioController.setMicIndicator(micIndicator);

        try {
            await this.populateAudioDevices();
            Logger.log("Audio devices loaded successfully.");
        } catch (error) {
            console.error(error);
            Logger.log("Failed to load audio devices.");
        }

        Logger.log(
            "Audio devices loaded successfully."
        );

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
                    joinButton.textContent = "Join";
                    return;
                }

                this.webSocketController.connect(usernameInput.value,
                    passwordInput.value,

                    async (connected, username) => {
                        if (connected) {

                            statusEl.textContent = "Connected as " + username;
                            statusEl.style.backgroundColor = "#005f00";
                            joinButton.textContent = "Leave";
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
                            statusEl.textContent = "Disconnected";
                            statusEl.style.backgroundColor = "#5f0000";
                            micSelect.disabled = false;
                            speakerSelect.disabled = false;
                            this.pttController.reset();
                            joinButton.textContent = "Join";
                        }
                    }
                );
            }
        );

        muteBtn.addEventListener("click",
            () => {

                const muted = this.audioController.toggleMute();

                this.pttController.setMuted(muted);

                muteBtn.textContent = muted
                        ? "Unmute"
                        : "Mute";

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