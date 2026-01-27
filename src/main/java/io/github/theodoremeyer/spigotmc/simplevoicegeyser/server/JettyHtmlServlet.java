package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Needs to be worked on. Major bug with sound.
 * Partially Complete now!
 * HTML server page
 */
public class JettyHtmlServlet extends HttpServlet {
    /**
     * HTML Page
     * Needs to be worked on.
     * @param req request
     * @param resp http-response
     * @throws IOException Exception
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html");
        resp.getWriter().write("""
           <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>SimpleVoice-Geyser</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        background: #1e1e1e;
                        color: #f5f5f5;
                        display: flex;
                        flex-direction: column;
                        align-items: center;
                        padding: 20px;
                        margin: 0;
                        min-height: 100vh;
                    }
                    h1 {
                        color: #00d4ff;
                    }
                    form {
                        background: #2a2a2a;
                        padding: 20px;
                        border-radius: 8px;
                        box-shadow: 0 0 10px #000;
                        width: 100%;
                        max-width: 400px;
                        margin-bottom: 20px;
                    }
                    label, input, select {
                        display: block;
                        width: 100%;
                        margin-bottom: 10px;
                        font-size: 14px;
                    }
                    input, select {
                       padding: 8px;
                       border-radius: 4px;
                       border: none;
                    }
                    button {
                        padding: 10px 15px;
                        background-color: #00d4ff;
                        border: none;
                        color: #000;
                        border-radius: 4px;
                        cursor: pointer;
                    }
                    button:hover {
                        background-color: #00aacc;
                    }
                    #micControls {
                        display: flex;
                        align-items: center;
                        gap: 12px;
                        margin-bottom: 10px;
                    }

                    #muteBtn {
                        padding: 10px 18px;
                        font-weight: bold;
                        border-radius: 6px;
                    }

                    #muteBtn.muted {
                        background-color: #aa3333;
                        color: #fff;
                    }

                    #muteBtn.unmuted {
                        background-color: #33aa55;
                        color: #000;
                    }

                    #micIndicator {
                        width: 12px;
                        height: 12px;
                        border-radius: 50%;
                        background-color: #444; /* idle */
                        border: 1px solid #666;
                    }

                    #micIndicator.active {
                        background-color: #33ff66;
                    }
                    #status {
                        margin: 10px;
                        padding: 8px 12px;
                        border-radius: 4px;
                        background: #333;
                        border: 1px solid #666;
                    }
                    #log {
                        width: 90%;
                        max-width: 600px;
                        height: 200px;
                        overflow-y: auto;
                        background: #2c2c2c;
                        border: 1px solid #555;
                        padding: 10px;
                        margin: 10px 0;
                        white-space: pre-wrap;
                        font-family: monospace;
                    }
                    #inputArea {
                        display: flex;
                        justify-content: center;   /* center contents */
                        align-items: center;
                        gap: 10px;
                        margin: 10px auto;         /* center container */
                        width: 90%;
                        max-width: 600px;
                    }
                </style>
            </head>
            <body>
                <h1>Join Simple Voice Chat</h1>
                <form id="joinForm">
                    <label for="username">Username:</label>
                    <input type="text" id="username" name="username" required autocomplete="username">

                    <label for="password">Password:</label>
                    <input type="password" id="password" name="password" required autocomplete="password">
       
                    <label for="mic">Select Microphone:</label>
                    <select id="mic" name="mic"></select>

                    <label for="speaker">Select Speaker:</label>
                    <select id="speaker" name="speaker"></select>

                    <button type="submit" id="joinbtn">Join</button>
                </form>

                <div id="micControls">
                    <button id="muteBtn" class="unmuted">Mute</button>
                    <span id="micIndicator" title="Mic activity"></span>
                </div>

                <div id="status">Waiting to join...</div>
                <div id="log"></div>

                <div id="inputArea">
                    <label for="msgInput"></label>
                    <input type="text" id="msgInput" placeholder="Type message..." />
                    <button onclick="sendMessage()">Send</button>
                </div>
                <button id="testSound" type="button">Test Sound</button>
                <script>
                    const form = document.getElementById('joinForm');
                    const logEl = document.getElementById('log');
                    const statusEl = document.getElementById('status');
                    const inputEl = document.getElementById('msgInput');
                    const speakerSelect = document.getElementById('speaker');
                    const micSelect = document.getElementById('mic');
                    const joinButton = document.getElementById('joinbtn');

                    const muteBtn = document.getElementById('muteBtn');
                    const micIndicator = document.getElementById('micIndicator');

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

                    let seq = 0;

                    // Load the AudioWorklet processor
                    async function initAudioWorklet() {
                        try {
                            if (audioContext.sampleRate !== 48000) {
                                console.warn("WRONG SAMPLE RATE: " + audioContext.sampleRate);
                                //audioContext.sampleRate = 48000;
                            }

                            await audioContext.audioWorklet.addModule('/audio-worklet-processor.js');
                            await audioContext.audioWorklet.addModule('/mic-capture-processor.js');

                            audioWorkletNode = new AudioWorkletNode(audioContext, 'pcm-player');

                            // Bridge node (do NOT play directly)
                            //const destination = audioContext.createMediaStreamDestination();
                            //audioWorkletNode.connect(destination);
                            audioWorkletNode.connect(audioContext.destination);

                            // Create hidden audio element for sink selection
                            window.audioElement = document.createElement("audio");
                            //audioElement.srcObject = destination.stream;
                            //audioElement.autoplay = true;
                            //audioElement.playsInline = true;
                            //audioElement.style.display = "none";
                            //document.body.appendChild(audioElement);
       
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

                    initAudioWorklet();

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

                                if (!speech || muted) return; // silence OR muted/ silence suppressed

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

                    function sendMessage() {
                        const msg = inputEl.value.trim();
                        if (ws && ws.readyState === WebSocket.OPEN && msg !== "") {
                            ws.send(JSON.stringify({ type: "chat", message: msg }));
                            log("[You] " + msg);
                            inputEl.value = "";
                        }
                    }

                    function log(msg) {
                        const time = new Date().toLocaleTimeString();
                        logEl.textContent += `\\n[${time}] ${msg}`;
                        logEl.scrollTop = logEl.scrollHeight;
                    }

                    async function setOutputDevice(deviceId) {
                        if (!window.audioElement) {
                            console.warn("Audio element not initialized yet");
                            return;
                        }
                        if (!audioElement.setSinkId) {
                            console.warn("setSinkId not supported in this browser");
                            return;
                        }

                        try {
                            await audioElement.setSinkId(deviceId);
                         console.log("Output device set to", deviceId);
                        } catch (e) {
                            console.warn("Failed to set output device:", e);
                        }
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


                    window.addEventListener("DOMContentLoaded", () => {
                        populateSpeakers();
                        populateMicrophones();
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
                </script>
            </body>
            </html>
        """);
    }
}
