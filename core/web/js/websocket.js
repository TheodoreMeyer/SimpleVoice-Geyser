import {
    decodeSvgV2Frame,
    getAudioCapabilities,
    getAudioDecompileStats,
    warmupAudioDecompiler
} from "./audio/AudioByteDecompiler.js";
import {Logger} from "./utils/logger.js";
import {
    isBackendTransferStatus,
    isMicFrameFresh,
    isSupportedOpusChunk,
    micFormatFromAck,
    micFormatsForEncoder,
    MIC_OPUS_ENCODER_CONFIG
} from "./audio/mic-format.mjs";

export class SvgWebSocket {

    static MAX_RECONNECT_ATTEMPTS = 5;
    static MAX_MIC_FRAME_AGE_MS = 150;
    static MAX_PCM_BUFFERED_BYTES = 1920;
    static MAX_OPUS_BUFFERED_BYTES = 640;
    static DisconnectPolicy = {
        FATAL: new Set([4003, 4004, 4005]),
        NO_RECONNECT: new Set([4001, 4004, 4005, 4006]),
        TIMEOUT: 4002,
        SERVER_SHUTDOWN: 4006,
        OUTDATED: 4008
    };
    #eventListeners = {
        statusChange: [],
        message: []
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
        this.micEncoder = null;
        this.micEncoderGeneration = 0;
        this.micPendingCaptureTimes = [];
        this.#resetState();
    }

    initWebSocket() {
        void warmupAudioDecompiler();

        this.audioController.onMicData((frame) => this.#sendMicFrame(frame));
    }

    connect(username, password, onStatusChange) {
        this.lastCredentials = { username, password };
        this.#resetState();
        this.addEventListener("statusChange", onStatusChange);
        this.#createSocket();
    }

    addEventListener(type, listener, persistsAfterConnection = false) {
        if (!Object.hasOwn(this.#eventListeners, type)) {
            Logger.log(`Cannot add event listener for event "${type}" because it does not exist.`)
            return;
        }
        this.#eventListeners[type].push({
            func: listener,
            isPersistent: persistsAfterConnection
        });
    }
    #runEventListeners(type, ...args) {
        this.#eventListeners[type].forEach(listener => {
            try {
                Promise.resolve(listener.func(...args)).catch(error => {
                    Logger.debug(`[WebSocket] Event listener failed: ${error?.message || error}`);
                });
            } catch (error) {
                Logger.debug(`[WebSocket] Event listener failed: ${error?.message || error}`);
            }
        });
    }
    #removeTemporaryEventListeners() {
        for (const type in this.#eventListeners) {
            this.#eventListeners[type] = this.#eventListeners[type].filter(listener => listener.isPersistent);
        }
    }

    #resetState() {
        this.#destroyMicEncoder();
        this.reconnectAttempts = 0;
        this.manualClose = false;
        this.hasJoined = false;
        this.changingServer = false;
        this.fatalAuthError = false;
        this.capabilitiesSent = false;
        this.micFormatAcknowledged = false;
        this.micPcmRenegotiationPending = false;
        this.selectedMicFormat = "pcm16le";
        this.audioController.setMicTransmissionEnabled(false);
        this.rxBinaryFrames = 0;
        this.rxBinaryBytes = 0;
        this.rxStereoFrames = 0;
        this.rxMonoFrames = 0;
        this.rxMalformedFrames = 0;
        this.rxSvgV2Frames = 0;
        this.rxLegacyFrames = 0;
        this.rxDecoderFallbacks = 0;
        this.reOpen = true;

        this.#removeTemporaryEventListeners();
    }

    #createSocket() {
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
                clientType: {
                    type: "Web",
                    serverVersion: window.PROJECT_VERSION || "unknown",
                    serverBuild: window.BUILD_ID || "unknown"
                }
            }));
            Logger.log("Connected.");
            this.reconnectAttempts = 0;

            this.#runEventListeners("statusChange", {
                connected: true,
                username: this.lastCredentials.username
            });
        };

        this.ws.onmessage = async (event) => {
            if (typeof event.data === "string") {
                try {
                    const data = JSON.parse(event.data);
                    const packetType = String(data.type || "").toLowerCase();
                    const msg = String(data.message || "").toLowerCase();

                    if (data?.fatal === true) {
                        this.fatalAuthError = true;
                        this.stopReconnection();
                    }

                    if (packetType === "status" && msg.includes("connected as")) {
                        this.changingServer = false;
                        this.hasJoined = true;
                        await this.#sendCapabilitiesOnce();
                        this.#runEventListeners("statusChange", {
                            connected: true,
                            username: this.lastCredentials?.username
                        });
                    } else if (packetType === "status" && msg.includes("changing server")) {
                        this.changingServer = true;
                        this.#runEventListeners("statusChange", {
                            connected: false,
                            changingServer: true,
                            username: this.lastCredentials?.username
                        });
                    }

                    if (packetType === "status" && isBackendTransferStatus(msg)) {
                        this.micFormatAcknowledged = false;
                        this.micPcmRenegotiationPending = false;
                        this.audioController.setMicTransmissionEnabled(false);
                        this.#destroyMicEncoder();
                    }

                    if (packetType === "capabilities_ack") {
                        Logger.log(`[AudioRX] Server selected transport mode: ${data.selectedMode || "legacy"}`);
                        await this.#applyMicFormatAck(data.selectedMicFormat);
                    }

                    if (packetType === "error") {
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

                    Logger.debug((packetType || "info") + ": " + (data.message || JSON.stringify(data)));

                    this.#runEventListeners("message", {
                        type: "json",
                        fatalAuthError: this.fatalAuthError,
                        packetType: packetType,
                        msg: msg
                    })
                } catch {
                    Logger.log("Received non-JSON message: " + event.type);
                    Logger.debug("Server: " + event.data);

                    this.#runEventListeners("message", {
                        type: "text",
                        eventType: event.type,
                        eventData: event.data
                    })
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
            this.micFormatAcknowledged = false;
            this.#destroyMicEncoder();
            this.#runEventListeners("statusChange", {
                connected: false,
                code: code,
                reason: reason
            });

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
                    this.#createSocket();
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
        this.audioController.setMicTransmissionEnabled(false);
        this.#destroyMicEncoder();
    }

    sendChat(msg) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN && !this.changingServer) {
            this.ws.send(JSON.stringify({ type: "chat", message: msg }));
        }
    }

    async #sendCapabilitiesOnce() {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN || this.capabilitiesSent) {
            return;
        }
        this.capabilitiesSent = true;
        const socket = this.ws;
        let audio = {
            protocols: ["legacy"],
            supportsOpusDecoder: false,
            secureContext: !!window.isSecureContext,
            decoder: {opusWasm: false, webCodecs: false},
            micFormats: ["pcm16le"]
        };

        try {
            const caps = await getAudioCapabilities();
            const runtime = this.audioController.getAudioRuntime();
            const canUseSvgV2 = caps.supportsSvgV2 && runtime.workletSupported;
            const canDecodeOpus = caps.supportsOpusDecoder && runtime.workletSupported;
            const supportsMicOpus = await this.#configureMicEncoder();
            audio = {
                protocols: canUseSvgV2 ? ["legacy", "svg-v2"] : ["legacy"],
                supportsOpusDecoder: canDecodeOpus,
                secureContext: caps.secureContext,
                decoder: caps.decoder,
                micFormats: micFormatsForEncoder(runtime.sampleRate, supportsMicOpus)
            };

            Logger.log(
                `[AudioRX] Client capabilities sent: ` +
                `svg-v2=${canUseSvgV2} opusDecoder=${canDecodeOpus} secure=${caps.secureContext}`
            );
        } catch (err) {
            this.rxDecoderFallbacks++;
            this.#destroyMicEncoder();
            Logger.log(`[AudioRX] Capability probe failed; sending PCM/legacy capabilities: ${err?.message || err}`);
        }

        if (this.ws !== socket || socket.readyState !== WebSocket.OPEN) return;
        this.lastCapabilities = {type: "capabilities", audio};
        socket.send(JSON.stringify(this.lastCapabilities));
    }

    async #configureMicEncoder() {
        if (this.audioController.getAudioRuntime().sampleRate !== 48000
            || typeof AudioEncoder === "undefined"
            || typeof AudioData === "undefined") {
            return false;
        }

        const config = MIC_OPUS_ENCODER_CONFIG;
        try {
            const support = await AudioEncoder.isConfigSupported(config);
            if (!support.supported || this.audioController.getAudioRuntime().sampleRate !== 48000) return false;
            this.#destroyMicEncoder();
            const generation = this.micEncoderGeneration;
            const captureTimes = this.micPendingCaptureTimes = [];
            this.micEncoder = new AudioEncoder({
                output: (chunk) => {
                    if (generation !== this.micEncoderGeneration
                        || !this.micFormatAcknowledged
                        || this.selectedMicFormat !== "opus") return;
                    if (!isSupportedOpusChunk(chunk)) {
                        void this.#renegotiatePcmMic(new Error("Opus encoder returned an unsupported packet"));
                        return;
                    }
                    // Chromium's Opus clock omits skipped input; retain the original capture clock.
                    const captureTimestamp = captureTimes.shift();
                    if (captureTimestamp === undefined) {
                        void this.#renegotiatePcmMic(new Error("Opus encoder returned an unmatched packet"));
                        return;
                    }
                    if (!this.ws || this.ws.readyState !== WebSocket.OPEN
                        || this.ws.bufferedAmount > SvgWebSocket.MAX_OPUS_BUFFERED_BYTES
                        || !isMicFrameFresh(
                            captureTimestamp,
                            this.audioController.getAudioContextTimeUs(),
                            SvgWebSocket.MAX_MIC_FRAME_AGE_MS
                        )) return;
                    const packet = new Uint8Array(chunk.byteLength);
                    chunk.copyTo(packet);
                    this.ws.send(packet);
                },
                error: (error) => {
                    if (generation === this.micEncoderGeneration) void this.#renegotiatePcmMic(error);
                }
            });
            this.micEncoder.configure(support.config || config);
            return true;
        } catch (error) {
            this.#destroyMicEncoder();
            Logger.log(`[AudioTX] Opus encoding unavailable: ${error?.message || error}`);
            return false;
        }
    }

    async #applyMicFormatAck(format) {
        this.audioController.setMicTransmissionEnabled(false);
        this.micFormatAcknowledged = false;
        this.selectedMicFormat = micFormatFromAck(format);
        if (this.selectedMicFormat === "opus" && !this.micEncoder && !await this.#configureMicEncoder()) {
            await this.#renegotiatePcmMic(new Error("Opus encoder could not be initialized"));
            return;
        }
        this.micPcmRenegotiationPending = false;
        this.micFormatAcknowledged = true;
        this.audioController.setMicTransmissionEnabled(true);
        Logger.log(`[AudioTX] Server selected microphone format: ${this.selectedMicFormat}`);
    }

    #sendMicFrame({samples, timestamp}) {
        if (!this.micFormatAcknowledged || this.changingServer || !this.ws || this.ws.readyState !== WebSocket.OPEN
            || !isMicFrameFresh(
                timestamp,
                this.audioController.getAudioContextTimeUs(),
                SvgWebSocket.MAX_MIC_FRAME_AGE_MS
            )) return;

        if (this.selectedMicFormat === "opus") {
            if (!this.micEncoder || this.micEncoder.encodeQueueSize >= 2
                || this.micPendingCaptureTimes.length >= 2
                || this.ws.bufferedAmount > SvgWebSocket.MAX_OPUS_BUFFERED_BYTES) return;
            const data = new AudioData({
                format: "f32-planar",
                sampleRate: 48000,
                numberOfFrames: 960,
                numberOfChannels: 1,
                timestamp,
                data: samples
            });
            try {
                this.micPendingCaptureTimes.push(timestamp);
                this.micEncoder.encode(data);
            } catch (error) {
                void this.#renegotiatePcmMic(error);
            } finally {
                data.close();
            }
            return;
        }

        if (this.ws.bufferedAmount > SvgWebSocket.MAX_PCM_BUFFERED_BYTES) return;

        const packet = new ArrayBuffer(1920);
        const view = new DataView(packet);
        for (let i = 0; i < 960; i++) {
            const sample = Math.max(-1, Math.min(1, samples[i]));
            view.setInt16(i * 2, sample < 0 ? sample * 0x8000 : sample * 0x7fff, true);
        }
        this.ws.send(packet);
    }

    #destroyMicEncoder() {
        this.micEncoderGeneration++;
        this.micPendingCaptureTimes.length = 0;
        if (this.micEncoder && this.micEncoder.state !== "closed") {
            try {
                this.micEncoder.close();
            } catch {
            }
        }
        this.micEncoder = null;
    }

    async #renegotiatePcmMic(error) {
        if (this.micPcmRenegotiationPending
            || (!this.micFormatAcknowledged && this.selectedMicFormat !== "opus")) return;
        this.micPcmRenegotiationPending = true;
        this.micFormatAcknowledged = false;
        this.audioController.setMicTransmissionEnabled(false);
        this.#destroyMicEncoder();
        Logger.log(`[AudioTX] Opus disabled; requesting PCM format: ${error?.message || error}`);

        const socket = this.ws;
        if (!this.lastCapabilities || !socket || socket.readyState !== WebSocket.OPEN) {
            this.micPcmRenegotiationPending = false;
            return;
        }
        const payload = {
            ...this.lastCapabilities,
            audio: {...this.lastCapabilities.audio, micFormats: ["pcm16le"]}
        };
        this.lastCapabilities = payload;
        socket.send(JSON.stringify(payload));
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
