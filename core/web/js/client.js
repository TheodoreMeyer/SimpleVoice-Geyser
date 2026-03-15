export function start() {
    const form = document.getElementById('joinForm');
    const logEl = document.getElementById('log');
    const statusEl = document.getElementById('status');
    const inputEl = document.getElementById('msgInput');
    const speakerSelect = document.getElementById('speaker');
    const micSelect = document.getElementById('mic');
    const joinButton = document.getElementById('joinbtn');

    const muteBtn = document.getElementById('muteBtn');
    const micIndicator = document.getElementById('micIndicator');

    const sendMessageBtn = document.getElementById("messageButton")

    let muted = false;

    const AudioCtx = window.AudioContext || window.webkitAudioContext;
    let audioContext = new AudioCtx({ sampleRate: 48000 });

    let ws = null;
    let microphoneStream = null;
    let audioWorkletNode = null;

    let micSendBuffer = new Int16Array(0);
    const MIC_PACKET_SIZE = 960;

    let micActiveUntil = 0;
    const MIC_HOLD_MS = 120;

    console.log("CLIENT.JS loaded!");

    // Load the AudioWorklet processor
    async function initAudioWorklet() {
        try {
            if (audioContext.sampleRate !== 48000) {
                console.warn("WRONG SAMPLE RATE: " + audioContext.sampleRate);
            }

            await audioContext.audioWorklet.addModule('/audio-worklet-processor.js');
            await audioContext.audioWorklet.addModule('/mic-capture-processor.js');

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

                if (speech && !muted) {
                    micActiveUntil = now + MIC_HOLD_MS;
                }

                if (!muted && now < micActiveUntil) {
                    micIndicator.classList.add("active");
                } else {
                    micIndicator.classList.remove("active");
                }

                if (!speech || muted) return; // silence OR muted

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