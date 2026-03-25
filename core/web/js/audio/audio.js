let audioContext;
let audioWorkletNode;

let micHandler = null;

let microphoneStream = null;
let micNode = null;
let micSource = null;

let muted = false;
let micActiveUntil = 0;
const MIC_HOLD_MS = 120;

let micBuffer = new Int16Array(0);
const PACKET_SIZE = 960;

let micIndicator = null;

export async function initAudio() {
    //const AudioCtx = window.AudioContext || window.webkitAudioContext;
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

    if (speech && !muted) {
        micActiveUntil = now + MIC_HOLD_MS;
    }

    if (micIndicator) {
        if (!muted && now < micActiveUntil) {
            micIndicator.classList.add("active");
        } else {
            micIndicator.classList.remove("active");
        }
    }

    if (!speech || muted) return;

    const int16 = new Int16Array(samples.length);
    for (let i = 0; i < samples.length; i++) {
        const s = Math.max(-1, Math.min(1, samples[i]));
        int16[i] = s < 0 ? s * 0x8000 : s * 0x7fff;
    }

    const merged = new Int16Array(micBuffer.length + int16.length);
    merged.set(micBuffer);
    merged.set(int16, micBuffer.length);
    micBuffer = merged;

    while (micBuffer.length >= PACKET_SIZE) {
        const packet = micBuffer.slice(0, PACKET_SIZE);
        micBuffer = micBuffer.slice(PACKET_SIZE);

        micHandler?.(packet.buffer);
    }
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

    micBuffer = new Int16Array(0);
    micActiveUntil = 0;

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
    micBuffer = new Int16Array(0);
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
            await audioElement.setSinkId(deviceId);
            return true;
        } catch {}
    }

    return false;
}