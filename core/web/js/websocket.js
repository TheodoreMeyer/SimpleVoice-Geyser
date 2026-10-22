import {
    decodeSvgV2Frame,
    getAudioCapabilities,
    getAudioDecompileStats,
    warmupAudioDecompiler
} from "./audio/AudioByteDecompiler.js";
import {applyMasterGainOnly, applyUserGain, noteHeard, setRoster} from "./audio/receive-volumes.js";
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
        message: [],
        voicePlayers: [],
        groups: []
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
        this._rxBinaryQueue = [];
        this._rxBinaryProcessing = false;
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
        this.#eventListeners[type].forEach(listener => listener.func(...args));
    }
    #removeTemporaryEventListeners() {
        for (const type in this.#eventListeners) {
            this.#eventListeners[type] = this.#eventListeners[type].filter(listener => listener.isPersistent);
        }
    }

    #resetState() {
        this.#destroyMicEncoder();
        this._rxBinaryQueue = [];
        this._rxBinaryProcessing = false;
        this.reconnectAttempts = 0;
        this.manualClose = false;
        this.hasJoined = false;
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
            Logger.log("WebSocket open; joining...");
            this.reconnectAttempts = 0;
            // UI "connected" waits for STATUS "Connected as ..." (pending Minecraft join possible).
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

                    if (packetType === "status" && (
                        msg.includes("waiting for you to join minecraft")
                        || msg.includes("waiting for voice chat")
                    )) {
                        // Keep the socket open; server is holding a pending join until MC/SVC ready.
                        Logger.log(msg.includes("voice chat")
                            ? "Waiting for voice chat..."
                            : "Waiting for Minecraft join...");
                        this.#runEventListeners("statusChange", {
                            connected: false,
                            waitingForMinecraft: true
                        });
                    }

                    if (packetType === "status" && msg.includes("connected as")) {
                        this.hasJoined = true;
                        try {
                            sessionStorage.removeItem("svgOutdatedReloadOnce");
                        } catch {
                            // ignore
                        }
                        await this.#sendCapabilitiesOnce();
                        this.#runEventListeners("statusChange", {
                            connected: true,
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

                    if (packetType === "voice_players") {
                        setRoster(data.players);
                        this.#runEventListeners("voicePlayers", data.players);
                    }

                    if (packetType === "groups") {
                        this.#runEventListeners("groups", {
                            groups: data.groups || [],
                            current: data.current ?? null,
                            members: data.members || [],
                            currentType: data.currentType ?? null,
                            currentHasPassword: !!data.currentHasPassword
                        });
                    }

                    if (packetType === "chat") {
                        const chatMsg = String(data.message || "");
                        if (chatMsg) {
                            Logger.log(chatMsg);
                        }
                    }

                    if (packetType === "error") {
                        const isFatalError = msg.includes("bedrock player to join") ||
                            msg.includes("use /svg pswd") ||
                            msg.includes("access denied:") ||
                            msg.includes("did not join the minecraft server in time") ||
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
                this.#enqueueBinaryFrame(event.data);
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
                const reloadKey = "svgOutdatedReloadOnce";
                if (!sessionStorage.getItem(reloadKey)) {
                    sessionStorage.setItem(reloadKey, "1");
                    Logger.log("Outdated client. Cache-bust reload once...");
                    // Bust caches via a temporary query param; the page strips it
                    // from the address bar on load (see index.html / v2.js).
                    const url = new URL(location.href);
                    url.searchParams.set("_svg", String(Date.now()));
                    location.replace(url.toString());
                    return;
                }
                sessionStorage.removeItem(reloadKey);
                Logger.log("Outdated client after reload — server web assets are stale.");
                alert(
                    "Update required: the website build does not match the plugin.\n\n" +
                    "On the server: stop, delete plugins/<SimpleVoice-Geyser>/web/, " +
                    "install the matching jar, start, then hard-refresh (Ctrl+F5)."
                );
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

            if (code === SvgWebSocket.DisconnectPolicy.TIMEOUT
                || reason === "join_wait_expired") {
                this.fatalAuthError = true;
                this.stopReconnection();
                Logger.log("Join wait expired / timeout disconnect.");
                return;
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
            // Do not stop reconnection here — onclose decides reconnect policy.
            Logger.log("WebSocket error occurred.");
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
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type: "chat", message: msg }));
        }
    }

    /**
     * @param {string} name
     * @param {string} [password]
     */
    joinGroup(name, password) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }
        const payload = { type: "join_group", name: String(name || "") };
        if (password) {
            payload.password = String(password);
        }
        this.ws.send(JSON.stringify(payload));
    }

    leaveGroup() {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify({ type: "leave_group" }));
        }
    }

    /**
     * @param {string} name
     * @param {string} [password]
     * @param {string} [groupType] isolated | normal | open (default isolated)
     */
    createGroup(name, password, groupType) {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
            return;
        }
        const payload = {
            type: "create_group",
            name: String(name || "").trim(),
            groupType: String(groupType || "isolated").trim().toLowerCase() || "isolated"
        };
        if (password) {
            payload.password = String(password);
        }
        this.ws.send(JSON.stringify(payload));
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
        if (!this.micFormatAcknowledged || !this.ws || this.ws.readyState !== WebSocket.OPEN
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

    /**
     * Serialize binary RX so Opus decode/play never overlaps across frames.
     * @param {ArrayBuffer} arrayBuffer
     */
    #enqueueBinaryFrame(arrayBuffer) {
        this._rxBinaryQueue.push(arrayBuffer);
        if (this._rxBinaryProcessing) {
            return;
        }
        this._rxBinaryProcessing = true;
        void this.#drainBinaryQueue();
    }

    async #drainBinaryQueue() {
        try {
            while (this._rxBinaryQueue.length > 0) {
                const frame = this._rxBinaryQueue.shift();
                await this.#handleIncomingBinaryFrame(frame);
            }
        } finally {
            this._rxBinaryProcessing = false;
            if (this._rxBinaryQueue.length > 0) {
                this._rxBinaryProcessing = true;
                void this.#drainBinaryQueue();
            }
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
            const senderUuid = v2Result.meta?.senderUuid || null;
            if (senderUuid) {
                noteHeard(senderUuid);
            }
            packet.senderId = senderUuid || packet.senderId || "other";
            if (packet?.samples) {
                applyUserGain(packet.samples, senderUuid);
            }
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
        // Legacy PCM has no sender UUID — master volume only; mix under one "legacy" voice.
        packet.senderId = "legacy";
        if (packet?.samples) {
            applyMasterGainOnly(packet.samples);
        }
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
