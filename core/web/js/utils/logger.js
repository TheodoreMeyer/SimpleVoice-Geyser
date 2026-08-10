/**
 * @import {ChatElements} from "./types.js"
 */

export class Logger {

    static handler = console.log;

    /**
     * @param {(msg: string) => void} handler
     */
    static setHandler(handler) {
        Logger.handler = handler;
    }

    /**
     * @param {string} msg
     */
    static log(msg) {
        Logger.handler(msg);
    }

    /**
     * @param {string} msg
     */
    static debug(msg) {
        console.log(`[DEBUG]: ` + msg);
    }
}

export class ChatLogger {

    /** @type {ChatElements} */
    options;

    /**
     * @param {ChatElements} options
     * @param {*} webSocketController
     */
    constructor(
        options,
        webSocketController
    ) {

        this.options = options;
        this.webSocketController =
            webSocketController;
    }

    init() {

        Logger.setHandler(
            this.handleMessage.bind(this)
        );

        this.options.sendBtn.addEventListener(
            "click",
            () => {

                const msg =
                    this.options.inputEl.value.trim();

                if (!msg) {
                    return;
                }

                this.webSocketController.sendChat(
                    msg
                );

                Logger.log(
                    "[You] " + msg
                );

                this.options.inputEl.value = "";
            }
        );
    }

    /**
     * @param {string} msg
     */
    handleMessage(msg) {

        const time =
            new Date()
                .toLocaleTimeString();

        this.options.logEl.textContent +=
            `\n[${time}] ${msg}`;

        this.options.logEl.scrollTop =
            this.options.logEl.scrollHeight;
    }
}