import {getAudioRuntime, onMicData, playAudio, resetAudioState} from "./audio/audio.js";
import {
    decodeSvgV2Frame,
    getAudioCapabilities,
    getAudioDecompileStats,
    warmupAudioDecompiler
} from "./audio/AudioByteDecompiler.js";
import {log} from "./utils/logger.js";

let ws = null;
let reconnectTimeout = null;
let lastCredentials = null;
let manualClose = false;
let hasJoined = false;
let fatalAuthError = false;
let reconnectAttempts = 0;
let rxBinaryFrames = 0;
let rxBinaryBytes = 0;
let rxStereoFrames = 0;
let rxMonoFrames = 0;
let rxMalformedFrames = 0;
let rxSvgV2Frames = 0;
let rxLegacyFrames = 0;
let rxDecoderFallbacks = 0;
let capabilitiesSent = false;

const MAX_RECONNECT_ATTEMPTS = 5;
let reOpen = true;

const DisconnectPolicy = {
    FATAL: new Set([4003, 4004, 4005]),
    NO_RECONNECT: new Set([4001, 4004, 4005, 4006]),
    TIMEOUT: 4002,
    SERVER_SHUTDOWN: 4006,
    OUTDATED: 4008
};

export function initWebSocket() {
    void warmupAudioDecompiler();

    onMicData((packet) => {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(packet);
        }
    });
}

export function connect(username, password, onStatusChange) {
    lastCredentials = { username, password };
    manualClose = false;
    reOpen = true;
    hasJoined = false;
    fatalAuthError = false;
    reconnectAttempts = 0;
    capabilitiesSent = false;
    rxBinaryFrames = 0;
    rxBinaryBytes = 0;
    rxStereoFrames = 0;
    rxMonoFrames = 0;
    rxMalformedFrames = 0;
    rxSvgV2Frames = 0;
    rxLegacyFrames = 0;
    rxDecoderFallbacks = 0;
    createSocket(onStatusChange);
}

function createSocket(onStatusChange) {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const pageUrl = new URL(window.location.href);
    if (!pageUrl.pathname.endsWith("/")) {
        const lastSegment = pageUrl.pathname.substring(pageUrl.pathname.lastIndexOf("/") + 1);
        const looksLikeFile = lastSegment.includes(".");
        pageUrl.pathname = looksLikeFile
            ? pageUrl.pathname.substring(0, pageUrl.pathname.lastIndexOf("/") + 1)
            : `${pageUrl.pathname}/`;
    }

    const wsUrl = new URL("ws", pageUrl);
    wsUrl.protocol = protocol;

    ws = new WebSocket(wsUrl.href);
    ws.binaryType = "arraybuffer";
    fatalAuthError = false;

    ws.onopen = () => {
        ws.send(JSON.stringify({
            type: "join",
            ...lastCredentials,
            build: window.BUILD_ID || "unknown"
        }));
        log("Connected.");
        reconnectAttempts = 0;
        onStatusChange(true, lastCredentials.username);
    };

    ws.onmessage = async (event) => {
        if (typeof event.data === "string") {
            try {
                const data = JSON.parse(event.data);
                const msg = String(data.message || "").toLowerCase();

                if (data?.fatal === true) {
                    fatalAuthError = true;
                    stopReconnection();
                }

                if (data.type === "status" && msg.includes("connected as")) {
                    hasJoined = true;
                    await sendCapabilitiesOnce();
                }

                if (data.type === "capabilities_ack") {
                    log(`[AudioRX] Server selected transport mode: ${data.selectedMode || "legacy"}`);
                }

                if (data.type === "error") {
                    const isFatalError = msg.includes("bedrock player to join") ||
                        msg.includes("use /svg pswd") ||
                        msg.includes("access denied:") ||
                        msg.includes("timeout") ||
                        msg.includes("left the game.");

                    if (isFatalError) {
                        fatalAuthError = true;
                        stopReconnection();

                        if (ws && ws.readyState === WebSocket.OPEN) {
                            ws.close();
                        }
                    }
                }

                if (msg.includes("left the game.")) {
                    stopReconnection();
                }

                log((data.type || "info") + ": " + (data.message || JSON.stringify(data)));
            } catch {
                log("Server: " + event.data);
            }
        } else {
            await handleIncomingBinaryFrame(event.data);
        }
    };

    ws.onclose = (event) => {
        const code = event.code;
        const reason = event.reason || "";

        log("Disconnected.");
        console.log("WebSocket closed:", code, reason);

        resetAudioState();
        onStatusChange(false);

        if (code === DisconnectPolicy.OUTDATED || reason === "update_required") {
            stopReconnection();
            log("Outdated client. Reloading...");
            alert("Update required. Reloading page.");
            location.reload();
            return;
        }

        // Fatal disconnect: hard stop.
        if (DisconnectPolicy.FATAL.has(code) || reason === "fatal") {
            fatalAuthError = true;
            stopReconnection();
            log("Fatal disconnect. Reconnect disabled.");
            return;
        }

        if (code === DisconnectPolicy.SERVER_SHUTDOWN) {
            stopReconnection();
            log("Server shutdown: " + reason);
            return;
        }

        if (code === DisconnectPolicy.TIMEOUT) {
            log("Timeout disconnect.");
        }

        if (DisconnectPolicy.NO_RECONNECT.has(code)) {
            stopReconnection();
            return;
        }

        const shouldReconnect = !manualClose
            && lastCredentials
            && reOpen
            && !fatalAuthError
            && reconnectAttempts < MAX_RECONNECT_ATTEMPTS;

        if (shouldReconnect) {
            reconnectAttempts++;
            reconnectTimeout = setTimeout(() => {
                log(`Reconnecting... (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})`);
                createSocket(onStatusChange);
            }, 3000);
        } else if (!manualClose && !hasJoined) {
            log("Stopped reconnecting after repeated pre-join failures.");
        }
    };

    ws.onerror = () => {
        log("WebSocket error occurred.");

        if (ws.readyState !== WebSocket.OPEN) {
            stopReconnection();
        }
    };
}

export function disconnect() {
    manualClose = true;
    lastCredentials = null;
    hasJoined = false;
    fatalAuthError = false;
    reconnectAttempts = 0;

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
    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
    }
}

async function sendCapabilitiesOnce() {
    if (!ws || ws.readyState !== WebSocket.OPEN || capabilitiesSent) {
        return;
    }
    capabilitiesSent = true;

    try {
        const caps = await getAudioCapabilities();
        const runtime = getAudioRuntime();
        const canUseSvgV2 = caps.supportsSvgV2 && runtime.workletSupported;
        const canDecodeOpus = caps.supportsOpusDecoder && runtime.workletSupported;
        ws.send(JSON.stringify({
            type: "capabilities",
            audio: {
                protocols: canUseSvgV2 ? ["legacy", "svg-v2"] : ["legacy"],
                supportsOpusDecoder: canDecodeOpus,
                secureContext: caps.secureContext,
                decoder: caps.decoder
            }
        }));

        log(
            `[AudioRX] Client capabilities sent: ` +
            `svg-v2=${canUseSvgV2} opusDecoder=${canDecodeOpus} secure=${caps.secureContext}`
        );
    } catch (err) {
        rxDecoderFallbacks++;
        log(`[AudioRX] Failed to report capabilities, using legacy fallback: ${err?.message || err}`);
    }
}

async function handleIncomingBinaryFrame(arrayBuffer) {
    rxBinaryFrames++;
    rxBinaryBytes += arrayBuffer.byteLength || 0;

    const v2Result = await decodeSvgV2Frame(arrayBuffer);
    if (v2Result) {
        if (v2Result.malformed) {
            rxMalformedFrames++;
            log(`[AudioRX] svg-v2 frame ignored: ${v2Result.reason || "malformed"}`);
            return;
        }

        rxSvgV2Frames++;
        const packet = v2Result.packet;
        if (packet.channels === 2) {
            rxStereoFrames++;
        } else {
            rxMonoFrames++;
        }
        playAudio(packet);
        maybeLogAudioStats();
        return;
    }

    rxLegacyFrames++;
    const packet = decodeLegacyPcm16(arrayBuffer);
    if (packet.channels === 2) {
        rxStereoFrames++;
    } else {
        rxMonoFrames++;
    }
    playAudio(packet);
    maybeLogAudioStats();
}

function maybeLogAudioStats() {
    if (rxBinaryFrames % 100 !== 0) {
        return;
    }
    const decompile = getAudioDecompileStats();
    log(
        `[AudioRX] frames=${rxBinaryFrames} bytes=${rxBinaryBytes} ` +
        `legacy=${rxLegacyFrames} svgV2=${rxSvgV2Frames} ` +
        `stereo=${rxStereoFrames} mono=${rxMonoFrames} malformed=${rxMalformedFrames} ` +
        `decodeErrors=${decompile.decodeErrors} fallbackReports=${rxDecoderFallbacks}`
    );
}

function decodeLegacyPcm16(arrayBuffer) {
    const view = new DataView(arrayBuffer);
    const byteLength = view.byteLength;

    if (byteLength % 2 !== 0) {
        rxMalformedFrames++;
    }
    const sampleCount = Math.floor(view.byteLength / 2);
    if (sampleCount <= 0) {
        return { samples: new Float32Array(0), channels: 1 };
    }

    const channels = byteLength % 4 === 0 ? 2 : 1;
    const out = new Float32Array(sampleCount);
    for (let i = 0; i < sampleCount; i++) {
        out[i] = view.getInt16(i * 2, true) / 32768;
    }

    return { samples: out, channels };
}
