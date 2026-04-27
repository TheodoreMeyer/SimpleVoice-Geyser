let audioContext;
let audioWorkletNode;

let micHandler = null;

let microphoneStream = null;
let micNode = null;
let micSource = null;

let muted = false;
let micActiveUntil = 0;
const MIC_HOLD_MS = 120;

//   FIX: ring buffer (NO reallocation)
const PACKET_SIZE = 960;
const BUFFER_SIZE = 960 * 20; // 20 packets max

let micBuffer = new Int16Array(BUFFER_SIZE);
let writeIndex = 0;
let readIndex = 0;
let available = 0;

let micIndicator = null;
let isPttActive = () => true;
let getTransmitMode = () => "voice"; // injected from controller

export async function initAudio() {
    audioContext = new AudioContext({ sampleRate: 48000 });

    if (audioContext.sampleRate !== 48000) {
        console.warn("WRONG SAMPLE RATE:", audioContext.sampleRate);
    }

    await audioContext.audioWorklet.addModule("/js/audio/speaker.js");
    await audioContext.audioWorklet.addModule("/js/audio/microphone.js");

    audioWorkletNode = new AudioWorkletNode(audioContext, "pcm-player");
    audioWorkletNode.connect(audioContext.destination);

    window.audioElement = document.createElement("audio");
}

export function setMicIndicator(el) {
    micIndicator = el;
}

export function onMicData(handler) {
    micHandler = handler;
}

export function setTransmitModeProvider(fn) {
    getTransmitMode = fn;
}

export function setPttActiveProvider(fn) {
    isPttActive = fn;
}

export async function startMic(deviceId) {
    await audioContext.resume();

    microphoneStream = await navigator.mediaDevices.getUserMedia({
        audio: {
            deviceId,
            noiseSuppression: true,
            echoCancellation: true,
            autoGainControl: true,
            channelCount: 1
        }
    });

    micSource = audioContext.createMediaStreamSource(microphoneStream);
    micNode = new AudioWorkletNode(audioContext, "mic-capture");

    micSource.connect(micNode);
    micNode.port.onmessage = handleMicMessage;
}

function handleMicMessage(event) {
    const { samples, speech } = event.data;
    const now = performance.now();

    const mode = getTransmitMode();   // "voice" | "ptt"
    const pttActive = isPttActive();

    // --- ALWAYS BUFFER (never gate this) ---
    for (let i = 0; i < samples.length; i++) {
        const s = Math.max(-1, Math.min(1, samples[i]));
        micBuffer[writeIndex] = s < 0 ? s * 0x8000 : s * 0x7fff;

        writeIndex = (writeIndex + 1) % BUFFER_SIZE;

        if (available < BUFFER_SIZE) {
            available++;
        } else {
            // overwrite oldest
            readIndex = (readIndex + 1) % BUFFER_SIZE;
        }
    }

    // --- UI INDICATOR (visual only) ---
    if (micIndicator) {
        if (mode === "ptt") {
            micIndicator.classList.toggle("active", !muted && pttActive);
        } else {
            if (speech && !muted) {
                micActiveUntil = now + MIC_HOLD_MS;
            }
            micIndicator.classList.toggle("active", now < micActiveUntil);
        }
    }

    // --- FIXED PACKET FLOW (always consistent timing) ---
    while (available >= PACKET_SIZE) {
        const packet = new Int16Array(PACKET_SIZE);

        for (let i = 0; i < PACKET_SIZE; i++) {
            packet[i] = micBuffer[readIndex];
            readIndex = (readIndex + 1) % BUFFER_SIZE;
        }

        available -= PACKET_SIZE;

        // --- TRANSMIT DECISION ---
        if (shouldSendPacket(mode, speech, pttActive)) {
            // avoid buffer reuse issues
            micHandler?.(packet.slice().buffer);
        }
    }
}

function shouldSendPacket(mode, speech, pttActive) {
    if (muted) return false;

    if (mode === "voice") {
        // VAD
        return speech;
    }

    if (mode === "ptt") {
        // Push-to-talk
        return pttActive;
    }

    return false;
}

export function stopMic() {
    if (micNode) {
        micNode.port.onmessage = null;
        micNode.disconnect();
        micNode = null;
    }

    if (micSource) {
        micSource.disconnect();
        micSource = null;
    }

    if (microphoneStream) {
        microphoneStream.getTracks().forEach(t => t.stop());
        microphoneStream = null;
    }

    // reset buffer
    writeIndex = 0;
    readIndex = 0;
    available = 0;

    if (micIndicator) {
        micIndicator.classList.remove("active");
    }
}

export function toggleMute() {
    muted = !muted;

    if (muted && micIndicator) {
        micIndicator.classList.remove("active");
    }

    return muted;
}

export function playAudio(buffer) {
    audioWorkletNode?.port.postMessage({ type: "pcm", buffer });
}

export function resetAudioState() {
    audioWorkletNode?.port.postMessage({ type: "reset" });

    writeIndex = 0;
    readIndex = 0;
    available = 0;
}

// audio.js
export async function getAudioDevices() {
    try {
        // Required so device labels are available
        await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch (error) {
        console.warn("Microphone permission denied:", error);
    }

    const devices = await navigator.mediaDevices.enumerateDevices();

    return {
        microphones: devices.filter(device => device.kind === "audioinput"),
        speakers: devices.filter(device => device.kind === "audiooutput")
    };
}

export async function setOutputDevice(deviceId) {
    if (audioContext.setSinkId) {
        try {
            await audioContext.setSinkId(deviceId);
            return true;
        } catch {}
    }

    if (window.audioElement?.setSinkId) {
        try {
            await window.audioElement.setSinkId(deviceId);
            return true;
        } catch {}
    }

    return false;
}