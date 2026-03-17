export function start() {
    const form = document.getElementById('joinForm');
    const logEl = document.getElementById('log');
    const statusEl = document.getElementById('status');
    const inputEl = document.getElementById('msgInput');
    const speakerSelect = document.getElementById('speaker');
    const micSelect = document.getElementById('mic');
    const joinButton = document.getElementById('joinbtn');
    const transmitModeSelect = document.getElementById('transmitMode');
    const pttBindingControls = document.getElementById('pttBindingControls');
    const bindPttBtn = document.getElementById('bindPttBtn');
    const clearPttBtn = document.getElementById('clearPttBtn');
    const pttBindingLabel = document.getElementById('pttBindingLabel');

    const muteBtn = document.getElementById('muteBtn');
    const micIndicator = document.getElementById('micIndicator');
    const pttControls = document.getElementById('pttControls');
    const pushToTalkBtn = document.getElementById('pushToTalkBtn');
    const fullscreenPttBtn = document.getElementById('fullscreenPttBtn');
    const pttFullscreenOverlay = document.getElementById('pttFullscreenOverlay');
    const pushToTalkFullscreenBtn = document.getElementById('pushToTalkFullscreenBtn');
    const exitFullscreenPttBtn = document.getElementById('exitFullscreenPttBtn');

    const sendMessageBtn = document.getElementById("messageButton")

    let muted = false;
    let bindingCaptureActive = false;
    let gamepadPollHandle = null;
    let fullscreenPttActive = false;

    const TRANSMIT_MODE_KEY = "svgTransmitMode";
    const PTT_BINDING_KEY = "svgPttBinding";
    const pttSources = new Set();
    const touchDevice = window.matchMedia("(pointer: coarse)").matches || navigator.maxTouchPoints > 0;

    let pttBinding = loadPttBinding();

    transmitModeSelect.value = localStorage.getItem(TRANSMIT_MODE_KEY) || "voice";

    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    let audioContext = new AudioCtx({ sampleRate: 48000 });

    let ws = null;
    let microphoneStream = null;
    let audioWorkletNode = null;

    let micSendBuffer = new Int16Array(0);
    const MIC_PACKET_SIZE = 960;

    let micActiveUntil = 0;
    const MIC_HOLD_MS = 120;

    const GAMEPAD_BUTTON_NAMES = {
        generic: [
            "Face Bottom (A / Cross / B)",
            "Face Right (B / Circle / A)",
            "Face Left (X / Square / Y)",
            "Face Top (Y / Triangle / X)",
            "Left Bumper",
            "Right Bumper",
            "Left Trigger",
            "Right Trigger",
            "View / Share",
            "Menu / Options",
            "Left Stick",
            "Right Stick",
            "D-Pad Up",
            "D-Pad Down",
            "D-Pad Left",
            "D-Pad Right",
            "Home",
            "Touchpad"
        ],
        xbox: [
            "A",
            "B",
            "X",
            "Y",
            "Left Bumper",
            "Right Bumper",
            "Left Trigger",
            "Right Trigger",
            "View",
            "Menu",
            "Left Stick",
            "Right Stick",
            "D-Pad Up",
            "D-Pad Down",
            "D-Pad Left",
            "D-Pad Right",
            "Xbox",
            "Share"
        ],
        playstation: [
            "Cross",
            "Circle",
            "Square",
            "Triangle",
            "L1",
            "R1",
            "L2",
            "R2",
            "Create",
            "Options",
            "L3",
            "R3",
            "D-Pad Up",
            "D-Pad Down",
            "D-Pad Left",
            "D-Pad Right",
            "PS",
            "Touchpad"
        ],
        nintendo: [
            "B",
            "A",
            "Y",
            "X",
            "L",
            "R",
            "ZL",
            "ZR",
            "Minus",
            "Plus",
            "Left Stick",
            "Right Stick",
            "D-Pad Up",
            "D-Pad Down",
            "D-Pad Left",
            "D-Pad Right",
            "Home",
            "Capture"
        ]
    };

    console.log("CLIENT.JS loaded!");

    function loadPttBinding() {
        try {
            const raw = localStorage.getItem(PTT_BINDING_KEY);
            if (!raw) return null;

            const parsed = JSON.parse(raw);
            if (!parsed || typeof parsed !== "object") return null;

            if (parsed.type === "keyboard" && typeof parsed.code === "string") return parsed;
            if (parsed.type === "mouse" && Number.isInteger(parsed.button)) return parsed;
            if (parsed.type === "gamepad" && Number.isInteger(parsed.buttonIndex)) return parsed;
        } catch (error) {
            console.warn("Failed to load PTT binding:", error);
        }

        return null;
    }

    function savePttBinding(binding) {
        pttBinding = binding;

        if (binding) {
            localStorage.setItem(PTT_BINDING_KEY, JSON.stringify(binding));
        } else {
            localStorage.removeItem(PTT_BINDING_KEY);
        }

        updatePttBindingLabel();
    }

    function getTransmitMode() {
        return transmitModeSelect.value === "ptt" ? "ptt" : "voice";
    }

    function isPttMode() {
        return getTransmitMode() === "ptt";
    }

    function isPttActive() {
        return !muted && pttSources.size > 0;
    }

    function addPttSource(sourceId) {
        pttSources.add(sourceId);
        updatePttButtons();
    }

    function removePttSource(sourceId) {
        pttSources.delete(sourceId);
        updatePttButtons();
    }

    function clearPttSources() {
        pttSources.clear();
        updatePttButtons();
    }

    function updatePttButtons() {
        const active = isPttActive();
        pushToTalkBtn.classList.toggle("active", active);
        pushToTalkFullscreenBtn.classList.toggle("active", active);
        pushToTalkBtn.textContent = active ? "Talking..." : "Hold to Talk";
        pushToTalkFullscreenBtn.textContent = active ? "Talking..." : "Hold to Talk";
    }

    function setBindingCaptureState(active) {
        bindingCaptureActive = active;
        bindPttBtn.textContent = active ? "Press a key, mouse button, or controller button..." : "Bind Push-to-Talk";
        bindPttBtn.disabled = active;
        clearPttBtn.disabled = active;

        if (active) {
            pttBindingLabel.textContent = "Waiting for input... press Escape to cancel.";
        } else {
            updatePttBindingLabel();
        }
    }

    function detectGamepadFamily(gamepadId = "") {
        const id = gamepadId.toLowerCase();
        if (id.includes("xbox") || id.includes("xinput")) return "xbox";
        if (id.includes("playstation") || id.includes("dualshock") || id.includes("dualsense") || id.includes("wireless controller")) return "playstation";
        if (id.includes("nintendo") || id.includes("switch") || id.includes("joy-con")) return "nintendo";
        return "generic";
    }

    function getGamepadButtonName(buttonIndex, gamepadId = "") {
        const family = detectGamepadFamily(gamepadId);
        return GAMEPAD_BUTTON_NAMES[family][buttonIndex] || GAMEPAD_BUTTON_NAMES.generic[buttonIndex] || `Button ${buttonIndex}`;
    }

    function getConnectedGamepad() {
        const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];
        return Array.from(gamepads).find(Boolean) || null;
    }

    function formatMouseButton(button) {
        if (button === 0) return "Mouse Left Button";
        if (button === 1) return "Mouse Middle Button";
        if (button === 2) return "Mouse Right Button";
        if (button === 3) return "Mouse Back Button";
        if (button === 4) return "Mouse Forward Button";
        return `Mouse Button ${button}`;
    }

    function formatKeyboardCode(code) {
        const aliases = {
            Space: "Space",
            Escape: "Escape",
            ShiftLeft: "Left Shift",
            ShiftRight: "Right Shift",
            ControlLeft: "Left Ctrl",
            ControlRight: "Right Ctrl",
            AltLeft: "Left Alt",
            AltRight: "Right Alt",
            MetaLeft: "Left Meta",
            MetaRight: "Right Meta"
        };

        if (aliases[code]) return aliases[code];
        return code
            .replace(/^Key/, "")
            .replace(/^Digit/, "")
            .replace(/([a-z])([A-Z])/g, "$1 $2");
    }

    function formatPttBinding(binding) {
        if (!binding) {
            return "No binding set. Use the hold button or add a binding.";
        }

        if (binding.type === "keyboard") {
            return `Bound to ${formatKeyboardCode(binding.code)}`;
        }

        if (binding.type === "mouse") {
            return `Bound to ${formatMouseButton(binding.button)}`;
        }

        if (binding.type === "gamepad") {
            const connectedGamepad = getConnectedGamepad();
            const buttonName = getGamepadButtonName(binding.buttonIndex, connectedGamepad ? connectedGamepad.id : "");
            return `Bound to Controller ${buttonName}`;
        }

        return "No binding set. Use the hold button or add a binding.";
    }

    function updatePttBindingLabel() {
        pttBindingLabel.textContent = formatPttBinding(pttBinding);
    }

    function updateTransmitModeUi() {
        const pttMode = isPttMode();
        pttBindingControls.hidden = !pttMode;
        pttControls.hidden = !pttMode;
        fullscreenPttBtn.hidden = !pttMode || !touchDevice;

        if (!pttMode) {
            clearPttSources();
            exitFullscreenPtt();
        }
    }

    function isEditableTarget(target) {
        if (!(target instanceof HTMLElement)) return false;
        if (target.isContentEditable) return true;

        const tagName = target.tagName;
        return tagName === "INPUT" || tagName === "TEXTAREA" || tagName === "SELECT";
    }

    function bindingMatchesKeyboard(event) {
        return pttBinding && pttBinding.type === "keyboard" && event.code === pttBinding.code;
    }

    function bindingMatchesMouse(event) {
        return pttBinding && pttBinding.type === "mouse" && event.button === pttBinding.button;
    }

    function setFullscreenPtt(active) {
        fullscreenPttActive = active;
        document.body.classList.toggle("fullscreen-ptt-active", active);
        pttFullscreenOverlay.classList.toggle("visible", active);
        pttFullscreenOverlay.setAttribute("aria-hidden", active ? "false" : "true");
    }

    async function requestFullscreenPtt() {
        if (!isPttMode()) return;

        setFullscreenPtt(true);

        if (pttFullscreenOverlay.requestFullscreen && document.fullscreenElement !== pttFullscreenOverlay) {
            try {
                await pttFullscreenOverlay.requestFullscreen();
            } catch (error) {
                console.warn("Fullscreen request failed:", error);
            }
        }
    }

    async function exitFullscreenPtt() {
        if (document.fullscreenElement === pttFullscreenOverlay && document.exitFullscreen) {
            try {
                await document.exitFullscreen();
            } catch (error) {
                console.warn("Exiting fullscreen failed:", error);
            }
        }

        setFullscreenPtt(false);
    }

    function capturePttBinding(binding) {
        savePttBinding(binding);
        setBindingCaptureState(false);
        log(`Push-to-talk binding saved: ${formatPttBinding(binding)}`);
    }

    function registerHoldButton(button, sourcePrefix) {
        const activePointers = new Set();

        button.addEventListener("pointerdown", (event) => {
            if (!isPttMode()) return;

            event.preventDefault();
            const sourceId = `${sourcePrefix}:${event.pointerId}`;
            activePointers.add(sourceId);
            addPttSource(sourceId);

            if (button.setPointerCapture) {
                button.setPointerCapture(event.pointerId);
            }
        });

        const releasePointer = (event) => {
            const sourceId = `${sourcePrefix}:${event.pointerId}`;
            activePointers.delete(sourceId);
            removePttSource(sourceId);
        };

        button.addEventListener("pointerup", releasePointer);
        button.addEventListener("pointercancel", releasePointer);
        button.addEventListener("lostpointercapture", releasePointer);
        button.addEventListener("contextmenu", (event) => event.preventDefault());
    }

    function pollGamepads() {
        const gamepads = navigator.getGamepads ? navigator.getGamepads() : [];

        if (bindingCaptureActive) {
            for (const gamepad of gamepads) {
                if (!gamepad) continue;

                const pressedIndex = gamepad.buttons.findIndex((button) => button.pressed || button.value > 0.5);
                if (pressedIndex !== -1) {
                    capturePttBinding({ type: "gamepad", buttonIndex: pressedIndex });
                    break;
                }
            }
        }

        if (pttBinding && pttBinding.type === "gamepad") {
            for (const gamepad of gamepads) {
                if (!gamepad) continue;

                const button = gamepad.buttons[pttBinding.buttonIndex];
                const sourceId = `gamepad:${gamepad.index}:${pttBinding.buttonIndex}`;
                if (button && (button.pressed || button.value > 0.5)) {
                    addPttSource(sourceId);
                } else {
                    removePttSource(sourceId);
                }
            }
        }

        gamepadPollHandle = window.requestAnimationFrame(pollGamepads);
    }

    // Load the AudioWorklet processor
    async function initAudioWorklet() {
        try {
            if (audioContext.sampleRate !== 48000) {
                console.warn("WRONG SAMPLE RATE: " + audioContext.sampleRate);
            }

            await audioContext.audioWorklet.addModule('/js/audio-worklet-processor.js');
            await audioContext.audioWorklet.addModule('/js/mic-capture-processor.js');

            audioWorkletNode = new AudioWorkletNode(audioContext, 'pcm-player');

            // Bridge node
            audioWorkletNode.connect(audioContext.destination);

            // Create hidden audio element for sink selection
            window.audioElement = document.createElement("audio");

            audioWorkletNode.port.onmessage = (event) => {
                if (event.data.type === 'log') {
                    console.log("[AudioWorklet]", event.data.message);
                } else if (event.data.type === 'stats') {
                    console.debug(
                        `[PLAYBACK] buffered=${event.data.buffered} underruns=${event.data.underruns}`
                    );
                }
            };

            console.log("AudioWorklet initialized and connected.");
        } catch (e) {
            console.error("Failed to load AudioWorklet:", e);
        }
    }

    async function populateSpeakers() {
        try {
            await navigator.mediaDevices.getUserMedia({ audio: true });
            const devices = await navigator.mediaDevices.enumerateDevices();
            speakerSelect.innerHTML = "";
            devices.forEach(device => {
                if (device.kind === "audiooutput") {
                    const option = document.createElement("option");
                    option.value = device.deviceId;
                    option.textContent = device.label || "Speaker " + (speakerSelect.length + 1);
                    speakerSelect.appendChild(option);
                }
            });
            const saved = localStorage.getItem("preferredSpeaker");
            if (saved) speakerSelect.value = saved;
            //await setOutputDevice(speakerSelect.value);
            await audioContextSetOutputDevice(speakerSelect.value);
        } catch (e) {
            console.error("Failed to list speakers:", e);
        }
    }

    async function populateMicrophones() {
        try {
            await navigator.mediaDevices.getUserMedia({ audio: true });
            const devices = await navigator.mediaDevices.enumerateDevices();
            micSelect.innerHTML = "";
            devices.forEach(device => {
                if (device.kind === "audioinput") {
                    const option = document.createElement("option");
                    option.value = device.deviceId;
                    option.textContent = device.label || "Microphone " + (micSelect.length + 1);
                    micSelect.appendChild(option);
                }
            });
            const savedMic = localStorage.getItem("preferredMic");
            if (savedMic) micSelect.value = savedMic;
        } catch (e) {
            console.error("Failed to list microphones:", e);
        }
    }

    speakerSelect.addEventListener("change", () => {
        localStorage.setItem("preferredSpeaker", speakerSelect.value);
    });

    micSelect.addEventListener("change", () => {
        localStorage.setItem("preferredMic", micSelect.value);
    });

    async function startMicrophone() {
        await audioContext.resume();
        try {
            microphoneStream = await navigator.mediaDevices.getUserMedia({
                audio: {
                    deviceId: micSelect.value,
                    noiseSuppression: true,
                    echoCancellation: true,
                    autoGainControl: true,
                    channelCount: 1
                }
            });

            const micSource = audioContext.createMediaStreamSource(microphoneStream);

            const micWorkletNode = new AudioWorkletNode(audioContext, 'mic-capture');

            micSource.connect(micWorkletNode);

            micWorkletNode.port.onmessage = (event) => {
                if (!ws || ws.readyState !== WebSocket.OPEN) return;

                const { samples, speech } = event.data;

                // Mic activity indicator
                const now = performance.now();

                const transmitting = isPttMode() ? isPttActive() : (speech && !muted);

                if (transmitting) {
                    micActiveUntil = now + MIC_HOLD_MS;
                }

                if (!muted && (isPttMode() ? transmitting : now < micActiveUntil)) {
                    micIndicator.classList.add("active");
                } else {
                    micIndicator.classList.remove("active");
                }

                if (!transmitting) return;

                // Float32 → Int16
                const int16Chunk = new Int16Array(samples.length);
                for (let i = 0; i < samples.length; i++) {
                    const s = Math.max(-1, Math.min(1, samples[i]));
                    int16Chunk[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
                }

                // ---- append to rolling buffer ----
                const merged = new Int16Array(micSendBuffer.length + int16Chunk.length);
                merged.set(micSendBuffer, 0);
                merged.set(int16Chunk, micSendBuffer.length);
                micSendBuffer = merged;

                if (micSendBuffer.length > MIC_PACKET_SIZE * 3) {
                    console.warn(
                        "[MIC] Backlog growing:",
                        micSendBuffer.length,
                        "samples"
                    );
                }

                // ---- send fixed 960-sample packets ----
                while (micSendBuffer.length >= MIC_PACKET_SIZE) {
                    const packet = micSendBuffer.slice(0, MIC_PACKET_SIZE);
                    ws.send(packet.buffer);

                    micSendBuffer = micSendBuffer.slice(MIC_PACKET_SIZE);
                }
            };

        } catch (e) {
            console.error("Microphone error:", e);
            alert("Microphone access is required.");
        }
    }

    muteBtn.addEventListener("click", () => {
        muted = !muted;

        if (muted) {
            clearPttSources();
            micActiveUntil = 0;
            micIndicator.classList.remove("active");

            muteBtn.textContent = "Unmute";
            muteBtn.classList.remove("unmuted");
            muteBtn.classList.add("muted");
        } else {
            muteBtn.textContent = "Mute";
            muteBtn.classList.remove("muted");
            muteBtn.classList.add("unmuted");
        }

        updatePttButtons();
    });

    transmitModeSelect.addEventListener("change", () => {
        localStorage.setItem(TRANSMIT_MODE_KEY, getTransmitMode());
        updateTransmitModeUi();
    });

    bindPttBtn.addEventListener("click", () => {
        setBindingCaptureState(true);
    });

    clearPttBtn.addEventListener("click", () => {
        savePttBinding(null);
        log("Push-to-talk binding cleared.");
    });

    fullscreenPttBtn.addEventListener("click", () => {
        requestFullscreenPtt();
    });

    exitFullscreenPttBtn.addEventListener("click", () => {
        exitFullscreenPtt();
    });

    registerHoldButton(pushToTalkBtn, "button");
    registerHoldButton(pushToTalkFullscreenBtn, "fullscreen");

    window.addEventListener("keydown", (event) => {
        if (bindingCaptureActive) {
            event.preventDefault();

            if (event.code === "Escape") {
                setBindingCaptureState(false);
                return;
            }

            capturePttBinding({ type: "keyboard", code: event.code });
            return;
        }

        if (!isPttMode() || !bindingMatchesKeyboard(event) || event.repeat) return;
        if (isEditableTarget(event.target)) return;

        event.preventDefault();
        addPttSource(`keyboard:${pttBinding.code}`);
    });

    window.addEventListener("keyup", (event) => {
        if (!isPttMode() || !bindingMatchesKeyboard(event)) return;

        event.preventDefault();
        removePttSource(`keyboard:${pttBinding.code}`);
    });

    window.addEventListener("mousedown", (event) => {
        if (bindingCaptureActive) {
            event.preventDefault();
            capturePttBinding({ type: "mouse", button: event.button });
            return;
        }

        if (!isPttMode() || !bindingMatchesMouse(event)) return;

        event.preventDefault();
        addPttSource(`mouse:${pttBinding.button}`);
    });

    window.addEventListener("mouseup", (event) => {
        if (!isPttMode() || !bindingMatchesMouse(event)) return;

        event.preventDefault();
        removePttSource(`mouse:${pttBinding.button}`);
    });

    window.addEventListener("auxclick", (event) => {
        if ((bindingCaptureActive || (isPttMode() && bindingMatchesMouse(event))) && event.cancelable) {
            event.preventDefault();
        }
    });

    window.addEventListener("contextmenu", (event) => {
        if ((bindingCaptureActive || (isPttMode() && bindingMatchesMouse(event))) && event.cancelable) {
            event.preventDefault();
        }
    });

    window.addEventListener("blur", () => {
        clearPttSources();
    });

    document.addEventListener("visibilitychange", () => {
        if (document.hidden) {
            clearPttSources();
        }
    });

    document.addEventListener("fullscreenchange", () => {
        setFullscreenPtt(document.fullscreenElement === pttFullscreenOverlay);
    });

    window.addEventListener("gamepadconnected", (event) => {
        log(`Controller connected: ${event.gamepad.id}`);
        updatePttBindingLabel();
    });

    window.addEventListener("gamepaddisconnected", (event) => {
        log(`Controller disconnected: ${event.gamepad.id}`);
        updatePttBindingLabel();
    });

    form.addEventListener('submit', async (event) => {
        event.preventDefault();

        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.close();
            ws = null;
            statusEl.textContent = "disconnected";
            statusEl.style.backgroundColor = "#5f0000";
            joinButton.textContent = "Join";

            micSelect.disabled = false;
            speakerSelect.disabled = false;

            if (microphoneStream) microphoneStream.getTracks().forEach(track => track.stop());

            clearPttSources();
            exitFullscreenPtt();

            return;
        }

        // CRITICAL: Resume the audio context on user gesture
        if (audioContext.state === 'suspended') {
            await audioContext.resume();
            await audioContextSetOutputDevice(speakerSelect.value);
            console.log("AudioContext resumed. Current state:", audioContext.state);
        }


        const data = { username: form.username.value, password: form.password.value };
        const protocol = location.protocol === "https:" ? "wss://" : "ws://";
        ws = new WebSocket(protocol + location.host + "/ws");
        ws.binaryType = 'arraybuffer';

        ws.onopen = () => {
            statusEl.textContent = "Connected as " + data.username;
            statusEl.style.backgroundColor = "#005f00";
            ws.send(JSON.stringify({ type: "join", ...data }));
            log("Connected.");
            joinButton.textContent = "Leave";
            micSelect.disabled = true;
            speakerSelect.disabled = true;
            startMicrophone();
        };

        ws.onmessage = (event) => {
            if (typeof event.data === 'string') {
                try {
                    const data = JSON.parse(event.data);
                    log((data.type || "info") + ": " + (data.message || JSON.stringify(data)));
                } catch (e) {
                    log("Server: " + event.data);
                }
            } else if (event.data instanceof ArrayBuffer) {
                // Send raw PCM to AudioWorklet
                const int16Data = new Int16Array(event.data);
                const float32Data = new Float32Array(int16Data.length);
                for (let i = 0; i < int16Data.length; i++) {
                    float32Data[i] = int16Data[i] / 32768;
                }
                if (audioWorkletNode) {
                    audioWorkletNode.port.postMessage({
                        type: 'pcm',
                        buffer: float32Data
                    });
                }
            }
        };

        ws.onclose = () => {
            statusEl.textContent = "Disconnected";
            statusEl.style.backgroundColor = "#5f0000";
            log("Disconnected.");
            joinButton.textContent = "Join";
            micSelect.disabled = false;
            speakerSelect.disabled = false;

            if (audioWorkletNode) {
                audioWorkletNode.port.postMessage({ type: 'reset' });
            }

            if (microphoneStream) microphoneStream.getTracks().forEach(track => track.stop());

            clearPttSources();
            exitFullscreenPtt();

            micActiveUntil = 0;
            micIndicator.classList.remove("active");
            muted = false;
            muteBtn.textContent = "Mute";
            muteBtn.classList.remove("muted");
            muteBtn.classList.add("unmuted");
        };

        ws.onerror = (err) => {
            console.error(err);
            statusEl.textContent = "WebSocket error";
            log("WebSocket error occurred.");
        };
    });

    sendMessageBtn.addEventListener("click", () => {
        const msg = inputEl.value.trim();
        if (ws && ws.readyState === WebSocket.OPEN && msg !== "") {
            ws.send(JSON.stringify({ type: "chat", message: msg }));
            log("[You] " + msg);
            inputEl.value = "";
        }
    });

    function log(msg) {
        const time = new Date().toLocaleTimeString();
        logEl.textContent += `\n[${time}] ${msg}`;
        logEl.scrollTop = logEl.scrollHeight;
    }

    async function audioContextSetOutputDevice(deviceId) {
        // Try AudioContext first (spec / future)
        if (audioContext.setSinkId) {
            try {
                await audioContext.setSinkId(deviceId);
                console.log("AudioContext output device set:", deviceId);
                return true;
            } catch (e) {
                console.warn("AudioContext.setSinkId failed:", e);
            }
        }

        // Fallback to HTMLAudioElement (Chrome reality)
        if (window.audioElement && audioElement.setSinkId) {
            try {
                await audioElement.setSinkId(deviceId);
                console.log("HTMLAudioElement output device set:", deviceId);
                return true;
            } catch (e) {
                console.warn("audioElement.setSinkId failed:", e);
            }
        }

        console.warn("setSinkId not supported");
        return false;
    }


    window.addEventListener("DOMContentLoaded", async () => {
        updatePttBindingLabel();
        updateTransmitModeUi();
        updatePttButtons();
        gamepadPollHandle = window.requestAnimationFrame(pollGamepads);

        await initAudioWorklet();
        await populateSpeakers();
        await populateMicrophones();
    });


    const testButton = document.getElementById("testSound");

    testButton.addEventListener("click", () => {

        if (!audioWorkletNode) {
            console.warn("AudioWorklet not initialized yet.");
            return;
        }

        // Generate a 440Hz sine wave for 0.5 seconds at 48kHz
        const sampleRate = 48000;
        const durationSec = 2.0;
        const length = sampleRate * durationSec;
        const float32Data = new Float32Array(length);

        for (let i = 0; i < length; i++) {
            float32Data[i] = Math.sin(2 * Math.PI * 440 * i / sampleRate) * 0.4; // 40% volume
        }

        audioWorkletNode.port.postMessage({ type: 'pcm', buffer: float32Data });
        console.log("Test sound sent to AudioWorklet");
    });
}