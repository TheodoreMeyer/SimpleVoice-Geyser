import {onMicData, playAudio, resetAudioState} from "./audio/audio.js";
import {log} from "./utils/logger.js";

let ws = null;
let reconnectTimeout = null;
let lastCredentials = null;
let manualClose = false;

let reOpen = true; // flag to control auto-reopening

export function initWebSocket() {
    onMicData((packet) => {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(packet);
        }
    });
}

export function connect(username, password, onStatusChange) {
    lastCredentials = { username, password };
    manualClose = false;
    reOpen = true; // Can reopen
    createSocket(onStatusChange);
}

function createSocket(onStatusChange) {
    const protocol = location.protocol === "https:" ? "wss://" : "ws://";
    ws = new WebSocket(protocol + location.host + "/ws");
    ws.binaryType = "arraybuffer";

    ws.onopen = () => {
        ws.send(JSON.stringify({ type: "join", ...lastCredentials }));
        log("Connected.");
        onStatusChange(true, lastCredentials.username);
    };

    ws.onmessage = (event) => {
        if (typeof event.data === "string") {
            try {

                const data = JSON.parse(event.data);

                // 🔴 detect auth failure BEFORE logging
                const msg = (data.message || "").toLowerCase();

                if (data.type === "error") {

                    const isFatalError = msg.includes("bedrock player to join") ||
                        msg.includes("Use /svg pswd") ||
                        msg.includes("access denied:") ||
                        msg.includes("timeout") ||
                        msg.includes("left the game.")
                    if (isFatalError) {
                        stopReconnection(); // prevent any further reconnect attempts

                        // optional: immediately close so onclose handles it cleanly
                        if (ws && ws.readyState === WebSocket.OPEN) {
                            ws.close();
                        }
                    }
                }

                if (msg.includes("left the game.")) {
                    stopReconnection(); // stop trying to reconnect if explicitly kicked
                }

                log((data.type || "info") + ": " + (data.message || JSON.stringify(data)));

            } catch {
                log("Server: " + event.data);
            }
        } else {
            //    STRICT PCM decode
            const int16 = new Int16Array(event.data);
            const float32 = new Float32Array(int16.length);

            for (let i = 0; i < int16.length; i++) {
                float32[i] = int16[i] / 32768;
            }

            playAudio(float32);
        }
    };

    ws.onclose = () => {
        log("Disconnected.");
        resetAudioState();
        onStatusChange(false);

        if (!manualClose && lastCredentials && reOpen) {
            reconnectTimeout = setTimeout(() => {
                log("Reconnecting...");
                createSocket(onStatusChange);
            }, 3000);
        }
    };

    ws.onerror = () => {
        log("WebSocket error occurred.");

        // If the socket never opened, treat as fatal
        if (ws.readyState !== WebSocket.OPEN) {
            stopReconnection();
        }
    };
}

export function disconnect() {
    manualClose = true;
    lastCredentials = null;

    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
    }

    if (ws) {
        ws.close();
        ws = null;
    }
}

export function sendChat(msg) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: "chat", message: msg }));
    }
}

export function isConnected() {
    return ws && ws.readyState === WebSocket.OPEN;
}

function stopReconnection() {
    reOpen = false;
    reconnectTimeout = null; // prevent any pending reconnects
}