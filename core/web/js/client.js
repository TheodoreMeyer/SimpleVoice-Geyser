import {initUI} from "./ui.js";
import {initWebSocket} from "./websocket.js";
import {initAudio} from "./audio/audio.js";

export async function start() {
    let audioRuntime = null;
    try {
        audioRuntime = await initAudio();
    } catch (error) {
        console.error("Audio initialization failed, running in degraded mode.", error);
    }
    initWebSocket();
    initUI(audioRuntime);
}
