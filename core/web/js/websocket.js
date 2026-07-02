import {
    decodeSvgV2Frame,
    getAudioCapabilities,
    getAudioDecompileStats,
    warmupAudioDecompiler
} from "./audio/AudioByteDecompiler.js";
import {Logger} from "./utils/logger.js";

export class SvgWebSocket {

    static MAX_RECONNECT_ATTEMPTS = 5;
    static DisconnectPolicy = {
        FATAL: new Set([4003, 4004, 4005]),
        NO_RECONNECT: new Set([4001, 4004, 4005, 4006]),
        TIMEOUT: 4002,
        SERVER_SHUTDOWN: 4006,
        OUTDATED: 4008
    };

    /**
     *
     * @param {SvgAudio}audioController
     */
    constructor(audioController) {
        this.audioController = audioController;
        this.ws = null;
        this.reconnectTimeout = null;
        this.lastCredentials = null;
        this.#resetState();
    }

    initWebSocket() {
        void warmupAudioDecompiler();

        this.audioController.onMicData((packet) => {
            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                this.ws.send(packet);
            }
        });
    }

    connect(username, password, onStatusChange) {
        this.lastCredentials = { username, password };
        this.#resetState();
        this.#createSocket(onStatusChange);
    }

    #resetState() {
        this.reconnectAttempts = 0;
        this.manualClose = false;
        this.hasJoined = false;
        this.fatalAuthError = false;
        this.capabilitiesSent = false;
        this.rxBinaryFrames = 0;
        this.rxBinaryBytes = 0;
        this.rxStereoFrames = 0;
        this.rxMonoFrames = 0;
        this.rxMalformedFrames = 0;
        this.rxSvgV2Frames = 0;
        this.rxLegacyFrames = 0;
        this.rxDecoderFallbacks = 0;
        this.reOpen = true;
    }

    #createSocket(onStatusChange) {
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

        this.ws = new WebSocket(wsUrl.href);
        this.ws.binaryType = "arraybuffer";
        this.fatalAuthError = false;

        this.ws.onopen = () => {
            this.ws.send(JSON.stringify({
                type: "join",
                ...this.lastCredentials,
                build: window.BUILD_ID || "unknown"
            }));
            Logger.log("Connected.");
            this.reconnectAttempts = 0;
            onStatusChange(true, this.lastCredentials.username);
        };

        this.ws.onmessage = async (event) => {
            if (typeof event.data === "string") {
                try {
                    const data = JSON.parse(event.data);
                    const msg = String(data.message || "").toLowerCase();

                    if (data?.fatal === true) {
                        this.fatalAuthError = true;
                        this.stopReconnection();
                    }

                    if (data.type === "status" && msg.includes("connected as")) {
                        this.hasJoined = true;
                        await this.#sendCapabilitiesOnce();
                    }

                    if (data.type === "capabilities_ack") {
                        Logger.log(`[AudioRX] Server selected transport mode: ${data.selectedMode || "legacy"}`);
                    }

                    if (data.type === "error") {
                        const isFatalError = msg.includes("bedrock player to join") ||
                            msg.includes("use /svg pswd") ||
                            msg.includes("access denied:") ||
                            msg.includes("timeout") ||
                            msg.includes("left the game.");

                        if (isFatalError) {
                            this.fatalAuthError = true;
                            this.stopReconnection();

                            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                                this.ws.close();
                            }
                        }
                    }

                    if (msg.includes("left the game.")) {
                        this.stopReconnection();
                    }

                    Logger.log((data.type || "info") + ": " + (data.message || JSON.stringify(data)));
                } catch {
                    Logger.log("Server: " + event.data);
                }
            } else {
                await this.#handleIncomingBinaryFrame(event.data);
            }
        };

        this.ws.onclose = (event) => {
            const code = event.code;
            const reason = event.reason || "";

            Logger.log("Disconnected.");
            console.log("WebSocket closed:", code, reason);

            this.audioController.resetAudioState();
            onStatusChange(false);

            if (code === SvgWebSocket.DisconnectPolicy.OUTDATED || reason === "update_required") {
                this.stopReconnection();
                Logger.log("Outdated client. Reloading...");
                alert("Update required. Reloading page.");
                location.reload();
                return;
            }

            // Fatal disconnect: hard stop.
            if (SvgWebSocket.DisconnectPolicy.FATAL.has(code) || reason === "fatal") {
                this.fatalAuthError = true;
                this.stopReconnection();
                Logger.log("Fatal disconnect. Reconnect disabled.");
                return;
            }

            if (code === SvgWebSocket.DisconnectPolicy.SERVER_SHUTDOWN) {
                this.stopReconnection();
                Logger.log("Server shutdown: " + reason);
                return;
            }

            if (code === SvgWebSocket.DisconnectPolicy.TIMEOUT) {
                Logger.log("Timeout disconnect.");
            }

            if (SvgWebSocket.DisconnectPolicy.NO_RECONNECT.has(code)) {
                this.stopReconnection();
                return;
            }

            const shouldReconnect = !this.manualClose
                && this.lastCredentials
                && this.reOpen
                && !this.fatalAuthError
                && this.reconnectAttempts < SvgWebSocket.MAX_RECONNECT_ATTEMPTS;

            if (shouldReconnect) {
                this.reconnectAttempts++;
                this.reconnectTimeout = setTimeout(() => {
                    Logger.log(`Reconnecting... (${this.reconnectAttempts}/${SvgWebSocket.MAX_RECONNECT_ATTEMPTS})`);
                    this.#createSocket(onStatusChange);
                }, 3000);
            } else if (!this.manualClose && !this.hasJoined) {
                Logger.log("Stopped reconnecting after repeated pre-join failures.");
            }
        };

        this.ws.onerror = () => {
            Logger.log("WebSocket error occurred.");

            if (this.ws.readyState !== WebSocket.OPEN) {
                this.stopReconnection();
            }
        };
    }

    isConnected() {
        return !!(
            this.ws &&
            this.ws.readyState === WebSocket.OPEN
        );
    }

    stopReconnection() {
        this.reOpen = false;
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }
    }

    disconnect() {
        this.manualClose = true;
        this.lastCredentials = null;
        this.hasJoined = false;
        this.fatalAuthError = false;
        this.reconnectAttempts = 0;

        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }

        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }

    sendChat(msg) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type: "chat", message: msg }));
        }
    }

    async #sendCapabilitiesOnce() {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN || this.capabilitiesSent) {
            return;
        }
        this.capabilitiesSent = true;

        try {
            const caps = await getAudioCapabilities();
            const runtime = this.audioController.getAudioRuntime();
            const canUseSvgV2 = caps.supportsSvgV2 && runtime.workletSupported;
            const canDecodeOpus = caps.supportsOpusDecoder && runtime.workletSupported;
            this.ws.send(JSON.stringify({
                type: "capabilities",
                audio: {
                    protocols: canUseSvgV2 ? ["legacy", "svg-v2"] : ["legacy"],
                    supportsOpusDecoder: canDecodeOpus,
                    secureContext: caps.secureContext,
                    decoder: caps.decoder
                }
            }));

            Logger.log(
                `[AudioRX] Client capabilities sent: ` +
                `svg-v2=${canUseSvgV2} opusDecoder=${canDecodeOpus} secure=${caps.secureContext}`
            );
        } catch (err) {
            this.rxDecoderFallbacks++;
            Logger.log(`[AudioRX] Failed to report capabilities, using legacy fallback: ${err?.message || err}`);
        }
    }

     async #handleIncomingBinaryFrame(arrayBuffer) {
        this.rxBinaryFrames++;
        this.rxBinaryBytes += arrayBuffer.byteLength || 0;

        const v2Result = await decodeSvgV2Frame(arrayBuffer);
        if (v2Result) {
            if (v2Result.malformed) {
                this.rxMalformedFrames++;
                Logger.debug(`[AudioRX] svg-v2 frame ignored: ${v2Result.reason || "malformed"}`);
                return;
            }

            this.rxSvgV2Frames++;
            const packet = v2Result.packet;
            if (packet.channels === 2) {
                this.rxStereoFrames++;
            } else {
                this.rxMonoFrames++;
            }
            this.audioController.playAudio(packet);
            this.#maybeLogAudioStats();
            return;
        }

         this.rxLegacyFrames++;
        const packet = this.#decodeLegacyPcm16(arrayBuffer);
        if (packet.channels === 2) {
            this.rxStereoFrames++;
        } else {
            this.rxMonoFrames++;
        }
        this.audioController.playAudio(packet);
         this.#maybeLogAudioStats();
    }

    #maybeLogAudioStats() {
        if (this.rxBinaryFrames % 100 !== 0) {
            return;
        }
        const decompile = getAudioDecompileStats();
        Logger.debug(
            `[AudioRX] frames=${this.rxBinaryFrames} bytes=${this.rxBinaryBytes} ` +
            `legacy=${this.rxLegacyFrames} svgV2=${this.rxSvgV2Frames} ` +
            `stereo=${this.rxStereoFrames} mono=${this.rxMonoFrames} malformed=${this.rxMalformedFrames} ` +
            `decodeErrors=${decompile.decodeErrors} fallbackReports=${this.rxDecoderFallbacks}`
        );
    }

    #decodeLegacyPcm16(arrayBuffer) {
        const view = new DataView(arrayBuffer);
        const byteLength = view.byteLength;

        if (byteLength % 2 !== 0) {
            this.rxMalformedFrames++;
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
}
