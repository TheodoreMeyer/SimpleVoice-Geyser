import {connect, disconnect, isConnected, sendChat} from "./websocket.js";
import {
    getAudioDevices,
    setMicIndicator,
    setOutputDevice,
    setPttActiveProvider,
    setTransmitModeProvider,
    startMic,
    stopMic,
    toggleMute
} from "./audio/audio.js";
import {log, setLogger} from "./utils/logger.js";
import {createPttController} from "./ptt.js";

export function initUI() {
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

    async function populateAudioDevices() {
        const { microphones, speakers } = await getAudioDevices();

        micSelect.innerHTML = "";
        speakerSelect.innerHTML = "";

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

        connect(form.username.value, form.password.value, (connected, username) => {
            if (connected) {
                statusEl.textContent = "Connected as " + username;
                statusEl.style.backgroundColor = "#005f00";
                joinButton.textContent = "Leave";

                micSelect.disabled = true;
                speakerSelect.disabled = true;

                startMic(micSelect.value);
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
            stopMic();
            await startMic(micSelect.value);
        }
    });
}