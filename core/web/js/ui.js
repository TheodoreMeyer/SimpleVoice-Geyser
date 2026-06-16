import {connect, disconnect, isConnected, sendChat} from "./websocket.js";
import {
    getAudioDevices,
    getAudioRuntime,
    setMicIndicator,
    setOutputDevice,
    setPttActiveProvider,
    setTransmitModeProvider,
    startMic,
    stopMic,
    toggleMute
} from "./audio/audio.js";
import {debug, log, setLogger} from "./utils/logger.js";
import {createPttController} from "./ptt.js";

export function initUI(initialAudioRuntime) {
    const form = document.getElementById('joinForm');
    const logEl = document.getElementById('log');
    const statusEl = document.getElementById('status');
    const inputEl = document.getElementById('msgInput');
    const speakerSelect = document.getElementById('speaker');
    const micSelect = document.getElementById('mic');
    const joinButton = document.getElementById('joinbtn');
    const muteBtn = document.getElementById('muteBtn');
    const micIndicator = document.getElementById('micIndicator');
    const sendBtn = document.getElementById("messageButton");
    const micCard = document.getElementById("micCard");
    const transmitModeSelect = document.getElementById("transmitModeSelect");
    const pttCard = document.getElementById("pttCard");
    const pttBindingControls = document.getElementById("pttBindingControls");
    const bindPttBtn = document.getElementById("bindPttBtn");
    const clearPttBtn = document.getElementById("clearPttBtn");
    const pttBindingLabel = document.getElementById("pttBindingLabel");
    const pttControls = document.getElementById("pttControls");
    const pushToTalkBtn = document.getElementById("pushToTalkBtn");
    const fullscreenPttBtn = document.getElementById("fullscreenPttBtn");
    const pttFullscreenOverlay = document.getElementById("pttFullscreenOverlay");
    const pushToTalkFullscreenBtn = document.getElementById("pushToTalkFullscreenBtn");
    const exitFullscreenPttBtn = document.getElementById("exitFullscreenPttBtn");
    const allowBackgroundPttCheckbox = document.getElementById("allowBackgroundPtt");

    //DEV UI
    const devToggle = document.getElementById("devToggle");
    const devContent = document.getElementById("devContent");
    devContent.classList.add("dev-hidden");

    devToggle.addEventListener("click", () => {
        const isHidden = devContent.classList.toggle("dev-hidden");

        devToggle.textContent = !isHidden
            ? "Developer Tools ▲"
            : "Developer Tools ▼";
    });

    setMicIndicator(micIndicator);

    function setSelectUnavailable(select, label) {
        select.innerHTML = "";
        const option = document.createElement("option");
        option.disabled = true;
        option.selected = true;
        option.textContent = label;
        select.appendChild(option);
        select.disabled = true;
    }

    async function populateAudioDevices() {
        const { microphones, speakers, available, reason } = await getAudioDevices();

        micSelect.innerHTML = "";
        speakerSelect.innerHTML = "";

        if (!available) {
            const fallbackReason = reason || "Audio device APIs are unavailable.";
            setSelectUnavailable(micSelect, "Microphone unavailable");
            setSelectUnavailable(speakerSelect, "Speaker unavailable");
            log(`[Audio] ${fallbackReason}`);
            return;
        }

        for (const mic of microphones) {
            const option = document.createElement("option");
            option.value = mic.deviceId;
            option.textContent =
                mic.label || `Microphone ${micSelect.options.length + 1}`;
            micSelect.appendChild(option);
        }

        for (const speaker of speakers) {
            const option = document.createElement("option");
            option.value = speaker.deviceId;
            option.textContent =
                speaker.label || `Speaker ${speakerSelect.options.length + 1}`;
            speakerSelect.appendChild(option);
        }

        if (microphones.length === 0) {
            setSelectUnavailable(micSelect, "No microphones detected");
        } else {
            micSelect.disabled = false;
        }

        if (speakers.length === 0) {
            setSelectUnavailable(speakerSelect, "No speakers detected");
        } else {
            speakerSelect.disabled = false;
        }

        const savedMic = localStorage.getItem("preferredMic");
        const savedSpeaker = localStorage.getItem("preferredSpeaker");

        if (savedMic && microphones.some(d => d.deviceId === savedMic)) {
            micSelect.value = savedMic;
        }

        if (savedSpeaker && speakers.some(d => d.deviceId === savedSpeaker)) {
            speakerSelect.value = savedSpeaker;
            await setOutputDevice(savedSpeaker);
        }
    }

    populateAudioDevices()
        .then(() => {
            log("Audio devices loaded successfully.");
        })
        .catch(error => {
            console.error(error);
            log("Failed to load audio devices.");
        });

    if (navigator.mediaDevices && typeof navigator.mediaDevices.addEventListener === "function") {
        navigator.mediaDevices.addEventListener("devicechange", () => {
            populateAudioDevices()
                .then(() => {
                    log("Audio device list refreshed.");
                })
                .catch(error => {
                    console.error(error);
                    log("Failed to refresh audio devices.");
                });
        });
    }

    setLogger((msg) => {
        const time = new Date().toLocaleTimeString();
        logEl.textContent += `\n[${time}] ${msg}`;
        logEl.scrollTop = logEl.scrollHeight;
    });

    const pttController = createPttController({
        elements: {
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
        },
        log
    });
    pttController.init();

    setTransmitModeProvider(() =>
        pttController.isPttMode() ? "ptt" : "voice"
    );

    setPttActiveProvider(() =>
        pttController.isPttActive()
    );

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        if (isConnected()) {
            disconnect();
            stopMic();
            pttController.reset();
            joinButton.textContent = "Join";
            return;
        }

        connect(form.username.value, form.password.value, async (connected, username) => {
            if (connected) {
                statusEl.textContent = "Connected as " + username;
                statusEl.style.backgroundColor = "#005f00";
                joinButton.textContent = "Leave";

                micSelect.disabled = true;
                speakerSelect.disabled = true;

                const runtime = getAudioRuntime();
                if (runtime.canCaptureMic) {
                    try {
                        await startMic(micSelect.value);
                    } catch (error) {
                        console.error(error);
                        log("Failed to start microphone. Receive/chat still available.");
                    }
                } else {
                    log("[Audio] Mic capture unsupported in this browser/context. Joined in compatibility mode.");
                }
            } else {
                statusEl.textContent = "Disconnected";
                statusEl.style.backgroundColor = "#5f0000";

                micSelect.disabled = false;
                speakerSelect.disabled = false;

                pttController.reset();
                joinButton.textContent = "Join";
            }
        });
    });

    sendBtn.addEventListener("click", () => {
        const msg = inputEl.value.trim();
        if (!msg) return;

        sendChat(msg);
        log("[You] " + msg);
        inputEl.value = "";
    });

    muteBtn.addEventListener("click", () => {
        const muted = toggleMute();
        pttController.setMuted(muted);

        muteBtn.textContent = muted ? "Unmute" : "Mute";
        muteBtn.classList.toggle("muted", muted);
        muteBtn.classList.toggle("unmuted", !muted);
    });

    speakerSelect.addEventListener("change", async () => {
        localStorage.setItem("preferredSpeaker", speakerSelect.value);
        await setOutputDevice(speakerSelect.value);
    });

    micSelect.addEventListener("change", async () => {
        localStorage.setItem("preferredMic", micSelect.value);
        if (isConnected()) {
            const runtime = getAudioRuntime();
            if (!runtime.canCaptureMic) {
                debug("[Audio] Mic capture unavailable, cannot switch microphone.");
                return;
            }
            stopMic();
            await startMic(micSelect.value);
        }
    });

    const runtime = initialAudioRuntime || getAudioRuntime();
    if (runtime.degradedReason) {
        log(`[Audio] ${runtime.degradedReason}`);
    }
}
