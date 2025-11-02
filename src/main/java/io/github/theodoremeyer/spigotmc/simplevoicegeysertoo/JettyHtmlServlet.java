package io.github.theodoremeyer.spigotmc.simplevoicegeysertoo;

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
        
                    //Audio playback setup
                    const mediaStreamDestination = audioContext.createMediaStreamDestination();
                    const audioElement = new Audio();
                    audioElement.srcObject = mediaStreamDestination.stream;
                    audioElement.autoplay = true;
                    audioElement.controls = false;
                    audioElement.style.display = "none";
                    audioElement.onplaying = () => {
                       console.log("Audio is playing to selected output");
                    };
                    document.body.appendChild(audioElement);
        
                    let audioQueue = [];
                    let isPlaying = false;
                    let nextStartTime = 0;
                    const bufferDuration = 0.04; //40 ms buffers
                    const targetQueueLength = 3;
                    const LOOKAHEAD_TIME = 0.1; //100ms ahead schedule
                    const SCHEDULE_INTERVAL = 25; //ms, how often scheduler runs
                    const adaptiveInterval = 25;
                    let schedulerInterval = null;
                    const MIN_QUEUE_SIZE_TO_START = 5;
                    let incomingChunkCount = 0;
                    let playedChunkCount = 0;
                    let sentChunkCount = 0;
                    setInterval(() => {
                       console.log(`Chunks received per second: ${incomingChunkCount}`);
                       console.log(`Chunks played per second: ${playedChunkCount}`);
                       console.log(`Chunks sent per second: ${sentChunkCount}`);
                       sentChunkCount = 0;
                       playedChunkCount = 0;
                       incomingChunkCount = 0;
                    }, 1000);
        
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
        
                    // Populate available microphones
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
        
                    //Event listener to save the selected microphone
                    micSelect.addEventListener("change", () => {
                       localStorage.setItem("preferredMic", micSelect.value);
                    });
        
                    // Start the microphone after the WebSocket connection
                    async function startMicrophone() {
                        try {
                           console.log("started mic");
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
                           const microphoneInput = audioContext.createMediaStreamSource(microphoneStream);
                           const bufferSize = 1024;
                           const processor = audioContext.createScriptProcessor(bufferSize, 1, 1)
                           microphoneInput.connect(processor);
                           processor.connect(audioContext.destination);
                           const keepAliveOscillator = audioContext.createOscillator();
                           keepAliveOscillator.frequency.value = 0; // inaudible
                           keepAliveOscillator.connect(audioContext.destination);
                           keepAliveOscillator.start();
                           let sampleBuffer = new Int16Array(0);
                           setInterval(() => {
                              console.log("currentTime", audioContext.currentTime);
                           }, 1000);
        
                           processor.onaudioprocess = (e) => {
                               if (ws && ws.readyState === WebSocket.OPEN) {
                                   const floatSamples = e.inputBuffer.getChannelData(0);
                                   // Convert float [-1,1] samples to Int16
                                   const newSamples = new Int16Array(floatSamples.length);
                                   for (let i = 0; i < floatSamples.length; i++) {
                                      let s = Math.max(-1, Math.min(1, floatSamples[i]));
                                      newSamples[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                                   }
                                    // Combine leftover samples with new samples
                                    const combined = new Int16Array(sampleBuffer.length + newSamples.length);
                                    combined.set(sampleBuffer);
                                    combined.set(newSamples, sampleBuffer.length);
        
                                    let offset = 0;
                                    // Send all full 960 sample chunks
                                    while (combined.length - offset >= 960) {
                                       sentChunkCount++;
                                       const chunk = combined.subarray(offset, offset + 960);
                                       ws.send(chunk.buffer);
                                       console.log("Sending chunk size:", chunk.byteLength); // should be 1920 in total
                                       console.log("sample rate", audioContext.sampleRate);
                                       offset += 960;
                                    }
        
                                    // Save leftover samples
                                    sampleBuffer = combined.subarray(offset);
                               } else {
                                   console.log("websocket Closed");
                               }
                           };
        
                          // Start recording
                          console.log("Microphone access granted and recording started.");
                        } catch (e) {
                           console.error("Error accessing microphone: ", e);
                           log("Couldn't access microphone");
                           alert("Microphone access is required to proceed.");
                        }
                    }

                    form.addEventListener('submit', (event) => {
                        event.preventDefault();

                        if (ws && ws.readyState === WebSocket.OPEN) {
                            log("Already connected.");
                            return;
                        }

                        const data = {
                            username: form.username.value,
                            password: form.password.value
                        };

                        ws = new WebSocket("ws://" + location.host + "/ws");
                        ws.binaryType = 'arraybuffer';

                        ws.onopen = () => {
                            statusEl.textContent = "Connected as " + data.username;
                            statusEl.style.backgroundColor = "#005f00";
                            ws.send(JSON.stringify({ type: "join", ...data }));
                            log("WebSocket connected.");
                            startMicrophone();
                            audioContext.resume(); //makes sure audiocontext is active
                            nextStartTime = audioContext.currentTime;
                            audioElement.play().then(() => {
                               console.log("Audio element playing");
                            }).catch(e => {
                               console.warn("Audio play prevented:", e);
                            });
                        };

                        ws.onmessage = (event) => {
                           if (typeof event.data === 'string') {
                              try {
                                const data = JSON.parse(event.data);
                                if (data.type === "status") {
                                    log("STATUS: " + data.message);
                                } else if (data.type === "error") {
                                    log("ERROR " + data.message);
                                } else if (data.type === "message") {
                                    log("Message:" + data.message);
                                } else {
                                    log("UnKnown " + data.message || event.data);
                                }
                              } catch (e) {
                                log("Server: " + event.data); // fallback for non-JSON
                              }
                           } else if (event.data instanceof ArrayBuffer) {
                              console.log("Received ArrayBuffer length:", event.data.byteLength);
                              incomingChunkCount++;
                              // Raw PCM audio
                              playPcm(event.data);
                           }
                        };

                        ws.onclose = () => {
                            statusEl.textContent = "Disconnected";
                            statusEl.style.backgroundColor = "#5f0000";
                            log("Disconnected.");
                            audioQueue = [];
                            isPlaying = false;
                            if (microphoneStream) {
                                microphoneStream.getTracks().forEach(track => track.stop());
                            }
                        };

                        ws.onerror = (error) => {
                            console.error(error);
                            statusEl.textContent = "WebSocket error";
                            log("WebSocket error occurred.");
                        };
                    });

                    //NOTE: this has a problem with creating a new line.
                    function sendMessage() {
                        const msg = inputEl.value.trim();
                        if (ws && ws.readyState === WebSocket.OPEN && msg !== "") {
                            ws.send(JSON.stringify({ type: "chat", message: msg }));
                            log("[You]" + msg);
                            inputEl.value = "";
                        }
                    }

                    function log(msg) {
                        const time = new Date().toLocaleTimeString();
                        logEl.textContent += `[${time}] ${msg}`;
                        logEl.scrollTop = logEl.scrollHeight;
                    }
      
                    async function playPcm(arrayBuffer) {
                        const MAX_QUEUE_LENGTH = 80;
                        if (audioQueue.length >= MAX_QUEUE_LENGTH) {
                            console.warn("Dropping audio buffer to avoid queue backlog");
                            return; // drop buffer to prevent lag buildup
                        }
                        const pcmData = new Int16Array(arrayBuffer);
                        const float32Data = new Float32Array(pcmData.length);
                        let sumSquares = 0;
                        for (let i = 0; i < pcmData.length; i++) {
                           float32Data[i] = pcmData[i] / 32768;
                           sumSquares += float32Data[i] * float32Data[i];
                        }
       
                        const rms = Math.sqrt(sumSquares / pcmData.length);
                        const SILENCE_THRESHOLD = 0.01;
      
                        if (rms < SILENCE_THRESHOLD) {
                           console.log("Skipping silent chunk, RMS:", rms.toFixed(5));
                           return; // Don't queue silent buffer
                        }

                       const buffer = audioContext.createBuffer(1, float32Data.length, 48000);
                       console.log("Buffer duration:", buffer.duration);
                       buffer.copyToChannel(float32Data, 0);
        
                        audioQueue.push(buffer);
                        console.log(`New chunk added. Queue length: ${audioQueue.length}`);
                        const MIN_QUEUE_SIZE_TO_START = 5; // Buffer 100ms (5 * 20ms)
                        if (!isPlaying && audioQueue.length >= MIN_QUEUE_SIZE_TO_START) {
                           nextStartTime = audioContext.currentTime;
                           isPlaying = true;
                           console.log("scheduling audio");
                           startScheduler(); // Start the scheduler loop when enough buffers are queued
                        }
                        console.log("isPlaying =", isPlaying);
                    }
      
                    function startScheduler() {
                       //if (schedulerInterval) return; // Already running, not used for now, replaced with below 3 lines
                       if (schedulerInterval) {
                          clearInterval(schedulerInterval); // Stop the old interval
                       }
                       //set adaptive interval dynamically based on the queue size
                       const adaptiveInterval = Math.max(20, (audioQueue.length / 10) * 20);
                       console.log("Adaptive Interval: " + adaptiveInterval + "ms");
      
                       schedulerInterval = setInterval(() => {
                          if (!isPlaying && audioQueue.length >= MIN_QUEUE_SIZE_TO_START) {
                             nextStartTime = audioContext.currentTime;
                             isPlaying = true;
                          }
                          // Dynamic lookahead time
                          const LOOKAHEAD_TIME = Math.min(Math.max(audioQueue.length * 0.02, 0.1), 0.3);
                          console.log(`LOOKAHEAD_TIME set to: ${LOOKAHEAD_TIME}s`);
                          if (isPlaying) {
                             // Schedule buffers that fit in lookahead_time
                             while (
                                audioQueue.length > 0 &&
                                nextStartTime < audioContext.currentTime + LOOKAHEAD_TIME
                             ) {
                                if (nextStartTime < audioContext.currentTime) {
                                    nextStartTime = audioContext.currentTime;
                                }
     
                                const buffer = audioQueue.shift();
                                const source = audioContext.createBufferSource();
                                source.buffer = buffer;
                                const gainNode = audioContext.createGain();
                                gainNode.gain.value = 1.0;
                                source.connect(gainNode);
                                gainNode.connect(audioContext.destination);
                                source.start(nextStartTime);
                                playedChunkCount++;
                                nextStartTime += buffer.duration;
    
                                source.onended = () => {
                                   // When queue drains, stop playing & stop scheduler
                                   if (audioQueue.length === 0) {
                                       isPlaying = false;
                                       stopScheduler();
                                       console.log("Audio queue drained, stopped playback.");
                                   }
                                };
                             }
                          }
                       }, adaptiveInterval); // Use dynamic interval to schedule the next cycle SCHEDULE_INTERVAL);
                    }
    
                    function stopScheduler() {
                       if (schedulerInterval) {
                           clearInterval(schedulerInterval);
                           schedulerInterval = null;
                       }
                    }
       
                    async function setOutputDevice(deviceId) {
                        if (audioElement.setSinkId) {
                            try {
                                 await audioElement.setSinkId(deviceId);
                                 console.log("Output device set to", deviceId);
                            } catch (e) {
                                 console.warn("⚠Failed to set output device:", e);
                            }
                        } else {
                           console.warn("Browser does not support setSinkId.");
                        }
                    }
        
                     window.addEventListener("DOMContentLoaded", () => {
                        populateSpeakers();
                        populateMicrophones();
                     });
      
                     document.getElementById('testSchedule').addEventListener('click', () => {
                         if (window.testScheduleInterval) {
                            clearInterval(window.testScheduleInterval);
                            window.testScheduleInterval = null;
                            console.log("Stopped Test Schedule");
                            return;
                         }
                         console.log("Starting Test Scheduler");
    
                         const sampleRate = 48000;
                         const chunkSize = 960;
                         const frequency = 440;
     
                         function generateChunk() {
                            const float32Data = new Float32Array(chunkSize);
                            for (let i = 0; i < chunkSize; i++) {
                               float32Data[i] = Math.sin(2 * Math.PI * frequency * (i / sampleRate));
                            }
                            const int16Data = new Int16Array(chunkSize);
                            for (let i = 0; i < chunkSize; i++) {
                               let s = Math.max(-1, Math.min(1, float32Data[i]));
                               int16Data[i] = s < 0 ? s * 0x8000 : s * 0x7FFF;
                            }
                            return int16Data.buffer;
                        }
      
                        window.testScheduleInterval = setInterval(() => {
                            const fakeBuffer = generateChunk();
                            playPcm(fakeBuffer);
                        }, 20);
                     });
                </script>
            </body>
            </html>
        """);
    }
}
