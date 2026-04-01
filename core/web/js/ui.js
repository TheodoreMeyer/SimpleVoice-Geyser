import {connect, disconnect, isConnected, sendChat} from "./websocket.js";
import {setMicIndicator, setOutputDevice, startMic, stopMic, toggleMute} from "./audio/audio.js";
import {log, setLogger} from "./utils/logger.js";

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

    //DEV UI
    const devToggle = document.getElementById("devToggle");
    const devContent = document.getElementById("devContent");

    let devOpen = false;

    devToggle.addEventListener("click", () => {
        devOpen = !devOpen;

        devContent.hidden = !devOpen;
        devToggle.textContent = devOpen
            ? "Developer Tools ▲"
            : "Developer Tools ▼";
    });
    // The rest

    setMicIndicator(micIndicator);

    setLogger((msg) => {
        const time = new Date().toLocaleTimeString();
        logEl.textContent += `\n[${time}] ${msg}`;
        logEl.scrollTop = logEl.scrollHeight;
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        if (isConnected()) {
            disconnect();
            stopMic();
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

        muteBtn.textContent = muted ? "Unmute" : "Mute";
        muteBtn.classList.toggle("muted", muted);
        muteBtn.classList.toggle("unmuted", !muted);
    });

    speakerSelect.addEventListener("change", async () => {
        localStorage.setItem("preferredSpeaker", speakerSelect.value);
        await setOutputDevice(speakerSelect.value);
    });

    micSelect.addEventListener("change", () => {
        localStorage.setItem("preferredMic", micSelect.value);
    });
}