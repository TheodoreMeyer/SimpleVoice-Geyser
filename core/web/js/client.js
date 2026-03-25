import {initUI} from "./ui.js";
import {initWebSocket} from "./websocket.js";
import {initAudio} from "./audio/audio.js";

export async function start() {
    await initAudio();
    initWebSocket();
    initUI();
}