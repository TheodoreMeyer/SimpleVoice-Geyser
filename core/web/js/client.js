import {SvgUI} from "./ui.js";
import {SvgWebSocket} from "./websocket.js";
import {SvgAudio} from "./audio/audio.js";
import {ChatLogger} from "./utils/logger.js";

/**
 * @import {SvgClientOptions} from "./types.js"
 * @import {SvgUIOptions} from "./util/internal-types.js"
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
     * Initializes the audio subsystem,
     * websocket connection, chat logger,
     * and user interface.
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

        this.chatLogger.init();

        try {
            this.audioRuntime = await this.audioController.initAudio();
        } catch (error) {
            console.error(
                "Audio initialization failed, running in degraded mode.",
                error
            );
        }

        this.webSocketController.initWebSocket();

        const uiOptions = /** @type {SvgUIOptions} */ {
            webSocketController: this.webSocketController,
            audioRuntime: this.audioRuntime,
            audioController: this.audioController,
            ...this.options.ui
        };

        this.ui = new SvgUI(uiOptions);
        await this.ui.init();
    }
}