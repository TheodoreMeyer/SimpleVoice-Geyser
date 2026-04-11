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
let shouldCaptureVoice = () => true;

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

export function setShouldCaptureVoice(predicate) {
    shouldCaptureVoice = typeof predicate === "function" ? predicate : () => true;
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
    const canTransmit = shouldCaptureVoice();

    if (speech && !muted && canTransmit) {
        micActiveUntil = now + MIC_HOLD_MS;
    }

    if (micIndicator) {
        if (!muted && now < micActiveUntil) {
            micIndicator.classList.add("active");
        } else {
            micIndicator.classList.remove("active");
        }
    }

    if (!speech || muted || !canTransmit) return;

    //   Float32 -> Int16
    for (let i = 0; i < samples.length; i++) {
        const s = Math.max(-1, Math.min(1, samples[i]));
        // write into ring buffer
        micBuffer[writeIndex] = s < 0 ? s * 0x8000 : s * 0x7fff;
        writeIndex = (writeIndex + 1) % BUFFER_SIZE;

        if (available < BUFFER_SIZE) {
            available++;
        } else {
            // overwrite oldest
            readIndex = (readIndex + 1) % BUFFER_SIZE;
        }
    }

    //   send fixed 960 sample packets ONLY
    while (available >= PACKET_SIZE) {
        const packet = new Int16Array(PACKET_SIZE);

        for (let i = 0; i < PACKET_SIZE; i++) {
            packet[i] = micBuffer[readIndex];
            readIndex = (readIndex + 1) % BUFFER_SIZE;
        }

        available -= PACKET_SIZE;

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