import {SvgUI} from "./ui.js";
import {SvgWebSocket} from "./websocket.js";
import {SvgAudio} from "./audio/audio.js";
import {ChatLogger} from "./utils/logger.js";
import {warmupAudioDecompiler, getAudioDecompileStats} from "./audio/AudioByteDecompiler.js";

/**
 * @import {SvgClientOptions} from "./utils/types.js"
 * @import {SvgUIOptions} from "./utils/internal-types.js"
 */

/**
 * Main entry point for the SVG web client.
 *
 * Creates and manages:
 * - Audio subsystem
 * - WebSocket connection
 * - User interface
 * - Chat logging
 *
 * @example
 * const client = new SvgClient({
 *     ui: {
 *         form: {...},
 *         audio: {...},
 *         ptt: {...},
 *         dev: {...}
 *     },
 *     chat: {...}
 * });
 *
 * await client.start();
 */
export class SvgClient {

    /**
     * @param {SvgClientOptions} options
     */
    constructor(options = {}) {
        this.audioController = null;
        this.audioRuntime = null;
        this.options = options;
        this.ui = null;
        this.webSocketController = null;
        this.chatLogger = null;
    }

    /**
     * Initializes core UI first, then optional subsystems independently so a
     * single rejected promise (e.g. Opus decoder) cannot abort the dashboard.
     *
     * @returns {Promise<void>}
     */
    async start() {
        this.audioController = new SvgAudio();
        this.webSocketController = new SvgWebSocket(this.audioController);

        this.chatLogger =
            new ChatLogger(
                this.options.chat,
                this.webSocketController
            );

        await this.#initializeCoreUi();

        const results = await Promise.allSettled([
            this.#initializeAppearance(),
            this.#initializeGroups(),
            this.#initializeChat(),
            this.#initializeAudioPlayback(),
            this.#initializeMicrophone()
        ]);

        for (const result of results) {
            if (result.status === "rejected") {
                console.error("SVG subsystem init failed:", result.reason);
            }
        }

        const decoder = getAudioDecompileStats();
        if (decoder?.error) {
            this.ui?.setDecoderError?.(
                "Audio playback decoder failed to load. Chat and groups still work."
            );
        }
    }

    async #initializeCoreUi() {
        // Chat logger must bind before UI so send handlers exist.
        this.chatLogger?.init();

        try {
            this.audioRuntime = await this.audioController.initAudio();
        } catch (error) {
            console.error(
                "Audio initialization failed, running in degraded mode.",
                error
            );
            this.audioRuntime = this.audioController.getAudioRuntime?.() || null;
        }

        // Wire mic → websocket once; playback decoder warms in parallel below.
        this.webSocketController.initWebSocket();

        const uiOptions = /** @type {SvgUIOptions} */ {
            webSocketController: this.webSocketController,
            audioRuntime: this.audioRuntime,
            audioController: this.audioController,
            chat: this.options.chat,
            ...this.options.ui
        };

        this.ui = new SvgUI(uiOptions);
        await this.ui.init();
    }

    async #initializeAppearance() {
        // AppearanceController is created inside SvgUI.init / layout init.
        return;
    }

    async #initializeGroups() {
        // GroupsController is created inside SvgUI.init.
        return;
    }

    async #initializeChat() {
        // Already initialized in core UI; slot retained for isolation contract.
        return;
    }

    async #initializeAudioPlayback() {
        await warmupAudioDecompiler();
        const stats = getAudioDecompileStats();
        if (stats?.error) {
            throw new Error(stats.error);
        }
    }

    async #initializeMicrophone() {
        // Mic capture starts after READY + WEB_VOICE via SvgUI.#maybeStartMic.
        // Keep this slot so a future eager probe cannot abort sibling modules.
        return;
    }
}
