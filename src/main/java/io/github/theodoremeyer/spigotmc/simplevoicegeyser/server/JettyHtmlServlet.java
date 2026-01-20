package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Needs to be worked on. Major bug with sound.
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
                        gap: 10px;
                        margin-top: 10px;
                        width: 90%;
                        max-width: 600px;
                    }
                </style>
            </head>
            <body>
                <h1>Join Simple Voice Chat</h1>
                <form id="joinForm">
                    <label for="username">Username:</label>
                    <input type="text" id="username" name="username" required>

                    <label for="password">Password:</label>
                    <input type="password" id="password" name="password">
       
                    <label for="mic">Select Microphone:</label>
                    <select id="mic" name="mic"></select>

                    <label for="speaker">Select Speaker:</label>
                    <select id="speaker" name="speaker"></select>

                    <button type="submit">Join</button>
                </form>

                <div id="status">Waiting to join...</div>
                <div id="log"></div>

                <div id="inputArea">
                    <input type="text" id="msgInput" placeholder="Type message..." />
                    <button onclick="sendMessage()">Send</button>
                </div>
                <button id="testSchedule" type="button">Test Schedule</button>
                <script>
                    const form = document.getElementById('joinForm');
                    const logEl = document.getElementById('log');
                    const statusEl = document.getElementById('status');
                    const inputEl = document.getElementById('msgInput');
                    const speakerSelect = document.getElementById('speaker');
                    const micSelect = document.getElementById('mic');

                    let audioContext = new (window.AudioContext || window.webkitAudioContext)();
                    let ws = null;
                    let microphoneStream = null;
                    let audioWorkletNode = null;

                    // Load the AudioWorklet processor
                    async function initAudioWorklet() {
                        try {
                            await audioContext.audioWorklet.addModule('/audio-worklet-processor.js');
                            audioWorkletNode = new AudioWorkletNode(audioContext, 'pcm-player');
                            audioWorkletNode.connect(audioContext.destination);
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
                            await setOutputDevice(speakerSelect.value);
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
                        try {
                            microphoneStream = await navigator.mediaDevices.getUserMedia({
                                audio: {
                                    deviceId: micSelect.value,
                                    noiseSuppression: true,
                                    echoCancellation: true,
                                    autoGainControl: true,
                                    sampleRate: 48000,
                                    channelCount: 1
                                }
                            });
                            const micSource = audioContext.createMediaStreamSource(microphoneStream);

                            // Send microphone audio to server
                            const processor = audioContext.createScriptProcessor(1024, 1, 1);
                            micSource.connect(processor);
                            processor.connect(audioContext.destination);

                            processor.onaudioprocess = (e) => {
                                if (!ws || ws.readyState !== WebSocket.OPEN) return;

                                const floatSamples = e.inputBuffer.getChannelData(0);
                                const int16Samples = new Int16Array(floatSamples.length);
                                for (let i = 0; i < floatSamples.length; i++) {
                                    const s = Math.max(-1, Math.min(1, floatSamples[i]));
                                    int16Samples[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                                }
                                ws.send(int16Samples.buffer);
                            };
                        } catch (e) {
                            console.error("Microphone error:", e);
                            alert("Microphone access is required.");
                        }
                    }

                    form.addEventListener('submit', (event) => {
                        event.preventDefault();
                        if (ws && ws.readyState === WebSocket.OPEN) {
                            log("Already connected.");
                            return;
                        }

                        const data = { username: form.username.value, password: form.password.value };
                        ws = new WebSocket("ws://" + location.host + "/ws");
                        ws.binaryType = 'arraybuffer';
        
                        ws.onopen = () => {
                            statusEl.textContent = "Connected as " + data.username;
                            statusEl.style.backgroundColor = "#005f00";
                            ws.send(JSON.stringify({ type: "join", ...data }));
                            log("WebSocket connected.");
                            startMicrophone();
                            audioContext.resume();
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
                            if (microphoneStream) microphoneStream.getTracks().forEach(track => track.stop());
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
                        const audioEl = document.querySelector('audio');
                        if (audioEl.setSinkId) {
                            try {
                                await audioEl.setSinkId(deviceId);
                                console.log("Output device set to", deviceId);
                            } catch (e) {
                                console.warn("Failed to set output device:", e);
                            }
                        }
                    }

                    window.addEventListener("DOMContentLoaded", () => {
                        populateSpeakers();
                        populateMicrophones();
                    });
                </script>
            </body>
            </html>
        """);
    }
}
