import {
    decodeSvgV2Frame,
    getAudioCapabilities,
    getAudioDecompileStats,
    warmupAudioDecompiler
} from "./audio/AudioByteDecompiler.js";
import { Logger } from "./utils/logger.js";

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
     * @param {import("./audio/audio.js").SvgAudio} audioController
     */
    constructor(audioController) {
        this.audioController = audioController;
        this.ws = null;
        this.reconnectTimeout = null;
        /** In-memory only — never persisted to storage. Cleared on logout / fatal auth. */
        this.lastCredentials = null;
        this.micDataHandlerInstalled = false;
        this.sessionMode = null;
        this.groupMessageHandler = null;
        this.operationResultHandler = null;
        this.sessionModeHandler = null;
        this.authenticatedHandler = null;
        this.#resetState();
    }

    initWebSocket() {
        void warmupAudioDecompiler();

        if (this.micDataHandlerInstalled) {
            return;
        }
        this.micDataHandlerInstalled = true;

        this.audioController.onMicData((packet, generation) => {
            // Only transmit after the server has confirmed the session is ready.
            if (!this.hasJoined || !this.ws || this.ws.readyState !== WebSocket.OPEN) {
                return;
            }
            // Native voice controller sessions must not send browser mic PCM.
            if (this.sessionMode === "NATIVE_VOICE_CONTROLLER") {
                return;
            }
            // Client privacy gate: no outbound voice without confirmed group membership.
            if (!this.inGroup) {
                return;
            }
            // Stale capture generation must never reach the wire.
            const activeGen = this.audioController.getActiveMicGeneration?.() || 0;
            if (generation != null && generation !== activeGen) {
                return;
            }
            if (activeGen !== this.audioController.getActiveMicGeneration?.()) {
                return;
            }
            this.ws.send(packet);
        });
    }

    /**
     * @param {(data: object) => void} handler
     */
    onGroupMessage(handler) {
        this.groupMessageHandler = handler;
    }

    /**
     * @param {(result: object) => void} handler
     */
    onOperationResult(handler) {
        this.operationResultHandler = handler;
    }

    /**
     * @param {(mode: string|null) => void} handler
     */
    onSessionMode(handler) {
        this.sessionModeHandler = handler;
    }

    /**
     * @param {() => void} handler
     */
    onAuthenticated(handler) {
        this.authenticatedHandler = handler;
    }

    connect(username, password, onStatusChange) {
        // Password kept in JS memory for reconnect only — never written to storage.
        this.lastCredentials = { username, password };
        this.#resetState();
        this.#createSocket(onStatusChange);
    }

    #resetState() {
        this.reconnectAttempts = 0;
        this.manualClose = false;
        this.hasJoined = false;
        this.inGroup = false;
        this.fatalAuthError = false;
        this.capabilitiesSent = false;
        this.groupsSubscribed = false;
        this.sessionMode = null;
        this.allowWebCreation = true;
        this.rxBinaryFrames = 0;
        this.rxBinaryBytes = 0;
        this.rxStereoFrames = 0;
        this.rxMonoFrames = 0;
        this.rxMalformedFrames = 0;
        this.rxSvgV2Frames = 0;
        this.rxLegacyFrames = 0;
        this.rxDecoderFallbacks = 0;
        this.reOpen = true;
        this.socketGeneration = (this.socketGeneration || 0) + 1;
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

        // Replace any previous socket so stale close handlers cannot corrupt the new session.
        this.#disposeSocket(false);

        const generation = this.socketGeneration;
        this.ws = new WebSocket(wsUrl.href);
        this.ws.binaryType = "arraybuffer";
        this.fatalAuthError = false;
        this.hasJoined = false;
        this.inGroup = false;
        this.capabilitiesSent = false;
        this.groupsSubscribed = false;
        this.sessionMode = null;

        this.ws.onopen = () => {
            if (!this.#isCurrentSocket(generation)) {
                return;
            }
            this.ws.send(JSON.stringify({
                type: "join",
                username: this.lastCredentials.username,
                password: this.lastCredentials.password,
                clientType: {
                    type: "Web",
                    serverVersion: window.PROJECT_VERSION || "unknown",
                    serverBuild: window.BUILD_ID || "unknown"
                }
            }));
            Logger.log("WebSocket open; waiting for server ready confirmation.");
            this.reconnectAttempts = 0;
            // Do not report Connected / start microphone until the server says ready.
            onStatusChange(false, this.lastCredentials.username, "connecting");
        };

        this.ws.onmessage = async (event) => {
            if (!this.#isCurrentSocket(generation)) {
                return;
            }
            if (typeof event.data === "string") {
                try {
                    const data = JSON.parse(event.data);
                    const packetType = String(data.type || "").toLowerCase();
                    const msg = String(data.message || "").toLowerCase();

                    if (data?.fatal === true) {
                        this.fatalAuthError = true;
                        this.stopReconnection();
                    }

                    if (packetType === "authenticated") {
                        this.authenticatedHandler?.();
                        onStatusChange(false, this.lastCredentials.username, "authenticating");
                    }

                    if (packetType === "session_mode") {
                        this.sessionMode = String(data.mode || "").toUpperCase() || null;
                        this.sessionModeHandler?.(this.sessionMode);
                    }

                    // Authoritative READY is the structured "ready" packet.
                    // Legacy STATUS "Connected as ..." is only a bounded fallback.
                    const isStructuredReady = packetType === "ready";
                    const isLegacyReadyFallback = packetType === "status"
                        && msg.includes("connected as");
                    const isReadyPacket = isStructuredReady || isLegacyReadyFallback;

                    if (isReadyPacket && !this.hasJoined) {
                        this.hasJoined = true;
                        if (!this.sessionMode && data.mode) {
                            this.sessionMode = String(data.mode).toUpperCase();
                            this.sessionModeHandler?.(this.sessionMode);
                        }
                        if (Object.prototype.hasOwnProperty.call(data, "allowWebCreation")) {
                            this.allowWebCreation = data.allowWebCreation !== false;
                        }
                        Logger.log("Connected.");
                        // Notify UI immediately — do not await audio/capabilities first.
                        onStatusChange(true, this.lastCredentials.username, "ready");
                        void this.#sendCapabilitiesOnce();
                        this.subscribeGroups();
                    } else if (packetType === "status" && data.message) {
                        Logger.debug("status received");
                    }

                    if (packetType === "capabilities_ack") {
                        Logger.log(`[AudioRX] Server selected transport mode: ${data.selectedMode || "legacy"}`);
                    }

                    if (
                        packetType === "groups_snapshot"
                        || packetType === "group_created"
                        || packetType === "group_removed"
                        || packetType === "membership_changed"
                    ) {
                        this.groupMessageHandler?.(data);
                    }

                    if (packetType === "operation_result") {
                        this.operationResultHandler?.(data);
                    }

                    if (packetType === "chat" && data.message) {
                        Logger.log(String(data.message));
                    }

                    if (packetType === "error") {
                        const isFatalError = this.#isFatalAuthError(msg, data?.fatal === true);

                        if (isFatalError) {
                            this.fatalAuthError = true;
                            this.stopReconnection();

                            if (this.ws && this.ws.readyState === WebSocket.OPEN) {
                                this.ws.close(4004, "fatal");
                            }
                        }

                        if (data.message && !msg.includes("password") && !msg.includes("credential")) {
                            Logger.log("Server error received.");
                        }

                        // Signal auth failure so UI can clear password.
                        if (!this.hasJoined) {
                            onStatusChange(false, this.lastCredentials?.username, "auth_failed");
                        }
                    }

                    if (msg.includes("left the game.")) {
                        this.stopReconnection();
                    }
                } catch {
                    Logger.log("Received non-JSON message: " + event.type);
                }
            } else if (this.hasJoined) {
                await this.#handleIncomingBinaryFrame(event.data);
            }
        };

        this.ws.onclose = (event) => {
            if (!this.#isCurrentSocket(generation)) {
                Logger.debug(`Ignoring stale socket close generation=${generation} code=${event.code}`);
                return;
            }

            const code = event.code;
            const reason = event.reason || "";

            Logger.log("Disconnected.");
            console.log("WebSocket closed:", code, reason);

            const wasJoined = this.hasJoined;
            this.hasJoined = false;
            this.inGroup = false;
            this.sessionMode = null;
            this.groupsSubscribed = false;
            this.audioController.resetAudioState();
            this.sessionModeHandler?.(null);

            if (code === SvgWebSocket.DisconnectPolicy.OUTDATED || reason === "update_required") {
                onStatusChange(false, undefined, "closed");
                this.stopReconnection();
                Logger.log("Outdated client. Reloading...");
                alert("Update required. Reloading page.");
                location.reload();
                return;
            }

            if (SvgWebSocket.DisconnectPolicy.FATAL.has(code) || reason === "fatal") {
                this.fatalAuthError = true;
                this.#clearStoredPassword();
                this.stopReconnection();
                Logger.log("Fatal disconnect. Reconnect disabled.");
                onStatusChange(
                    false,
                    undefined,
                    wasJoined ? "closed" : "auth_failed"
                );
                return;
            }

            if (code === SvgWebSocket.DisconnectPolicy.SERVER_SHUTDOWN) {
                this.stopReconnection();
                Logger.log("Server shutdown: " + reason);
                onStatusChange(false, undefined, "closed");
                return;
            }

            if (code === SvgWebSocket.DisconnectPolicy.TIMEOUT) {
                Logger.log("Timeout disconnect.");
            }

            if (SvgWebSocket.DisconnectPolicy.NO_RECONNECT.has(code)) {
                this.#clearStoredPassword();
                this.stopReconnection();
                onStatusChange(false, undefined, "closed");
                return;
            }

            const shouldReconnect = !this.manualClose
                && this.lastCredentials
                && this.lastCredentials.password
                && this.reOpen
                && !this.fatalAuthError
                && this.reconnectAttempts < SvgWebSocket.MAX_RECONNECT_ATTEMPTS;

            if (shouldReconnect) {
                this.reconnectAttempts++;
                onStatusChange(false, this.lastCredentials.username, "reconnecting");
                this.reconnectTimeout = setTimeout(() => {
                    Logger.log(`Reconnecting... (${this.reconnectAttempts}/${SvgWebSocket.MAX_RECONNECT_ATTEMPTS})`);
                    this.#createSocket(onStatusChange);
                }, 3000);
            } else {
                if (!this.manualClose && !wasJoined) {
                    this.#clearStoredPassword();
                    Logger.log("Stopped reconnecting after repeated pre-join failures.");
                }
                onStatusChange(false, undefined, "closed");
            }
        };

        this.ws.onerror = () => {
            if (!this.#isCurrentSocket(generation)) {
                return;
            }
            Logger.log("WebSocket error occurred.");

            if (this.ws.readyState !== WebSocket.OPEN) {
                this.stopReconnection();
            }
        };
    }

    #clearStoredPassword() {
        if (this.lastCredentials) {
            this.lastCredentials = {
                username: this.lastCredentials.username,
                password: ""
            };
        }
    }

    #isCurrentSocket(generation) {
        return this.ws != null && this.socketGeneration === generation;
    }

    #disposeSocket(markManual) {
        if (markManual) {
            this.manualClose = true;
        }
        if (this.reconnectTimeout) {
            clearTimeout(this.reconnectTimeout);
            this.reconnectTimeout = null;
        }
        if (this.ws) {
            const old = this.ws;
            old.onopen = null;
            old.onmessage = null;
            old.onerror = null;
            old.onclose = null;
            try {
                if (old.readyState === WebSocket.OPEN || old.readyState === WebSocket.CONNECTING) {
                    old.close(1000, "replaced");
                }
            } catch {
                // ignore
            }
            this.ws = null;
        }
    }

    #isFatalAuthError(msg, fatalFlag) {
        if (fatalFlag) {
            return true;
        }
        return msg.includes("bedrock player to join")
            || msg.includes("use /svg pswd")
            || msg.includes("you do not have permission")
            || msg.includes("must be online on the minecraft server")
            || msg.includes("left the game.");
    }

    /**
     * True only after the server has sent the authoritative ready status.
     */
    isConnected() {
        return !!(
            this.hasJoined &&
            this.ws &&
            this.ws.readyState === WebSocket.OPEN
        );
    }

    /**
     * @returns {boolean} whether the server has confirmed the session
     */
    isReady() {
        return this.isConnected();
    }

    /**
     * @returns {string|null}
     */
    getSessionMode() {
        return this.sessionMode;
    }

    /**
     * @returns {boolean}
     */
    getAllowWebCreation() {
        return this.allowWebCreation !== false;
    }

    /**
     * Client-side privacy gate for outbound mic frames.
     * @param {boolean} inGroup
     */
    setInGroup(inGroup) {
        this.inGroup = !!inGroup;
    }

    /**
     * @returns {boolean}
     */
    isInGroup() {
        return !!this.inGroup;
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
        this.inGroup = false;
        this.fatalAuthError = false;
        this.sessionMode = null;
        this.groupsSubscribed = false;
        this.reconnectAttempts = 0;
        this.#disposeSocket(true);
        this.sessionModeHandler?.(null);
    }

    sendChat(msg) {
        if (!this.isReady()) {
            Logger.log("Chat ignored: voice session is not ready yet.");
            return;
        }
        this.ws.send(JSON.stringify({ type: "chat", message: msg }));
    }

    subscribeGroups() {
        if (!this.isReady()) {
            return;
        }
        this.groupsSubscribed = true;
        this.ws.send(JSON.stringify({ type: "groups_subscribe" }));
    }

    sendGroupsRefresh(operationId) {
        if (!this.isReady()) {
            Logger.log("Groups refresh ignored: session not ready.");
            return false;
        }
        this.ws.send(JSON.stringify({ type: "groups_refresh", operationId }));
        return true;
    }

    sendGroupJoin(groupId, password, operationId) {
        if (!this.isReady()) {
            Logger.log("Group join ignored: session not ready.");
            return;
        }
        const payload = {
            type: "group_join",
            groupId,
            operationId
        };
        if (password != null && password !== "") {
            payload.password = password;
        }
        this.ws.send(JSON.stringify(payload));
    }

    sendGroupLeave(operationId, expectedGroupId) {
        if (!this.isReady()) {
            Logger.log("Group leave ignored: session not ready.");
            return;
        }
        const payload = { type: "group_leave", operationId };
        if (expectedGroupId) {
            payload.expectedGroupId = expectedGroupId;
        }
        this.ws.send(JSON.stringify(payload));
    }

    sendGroupCreate(name, password, type, operationId) {
        if (!this.isReady()) {
            Logger.log("Group create ignored: session not ready.");
            return false;
        }
        // IMPORTANT: packet discriminator is `type: "group_create"`.
        // Group kind must use a separate field (`groupType`) — a duplicate
        // `type` key would overwrite the packet type and the server would reject it.
        const payload = {
            type: "group_create",
            operationId,
            name,
            password: password != null && password !== "" ? password : null,
            groupType: type || "ISOLATED"
        };
        this.ws.send(JSON.stringify(payload));
        return true;
    }

    async #sendCapabilitiesOnce() {
        if (!this.ws || this.ws.readyState !== WebSocket.OPEN || this.capabilitiesSent || !this.hasJoined) {
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
                    decoder: caps.decoder,
                    sampleRate: runtime.sampleRate || undefined
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
