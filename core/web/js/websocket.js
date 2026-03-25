import {onMicData, playAudio, resetAudioState} from "./audio/audio.js";
import {log} from "./utils/logger.js";

let ws = null;
let reconnectTimeout = null;
let lastCredentials = null;

export function initWebSocket() {
    onMicData((packet) => {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(packet);
        }
    });
}

export function connect(username, password, onStatusChange) {
    lastCredentials = { username, password };
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
                log((data.type || "info") + ": " + (data.message || JSON.stringify(data)));
            } catch {
                log("Server: " + event.data);
            }
        } else {
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

        if (lastCredentials) {
            reconnectTimeout = setTimeout(() => {
                log("Reconnecting...");
                createSocket(onStatusChange);
            }, 3000);
        }
    };

    ws.onerror = () => {
        log("WebSocket error occurred.");
    };
}

export function disconnect() {
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