import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import test from "node:test";
import vm from "node:vm";
import {SvgAudio} from "../../../web/js/audio/audio.js";
import {SvgWebSocket} from "../../../web/js/websocket.js";
import {
    isBackendTransferStatus,
    isMicFrameFresh,
    isSupportedOpusChunk,
    micFormatFromAck,
    micFormatsForEncoder,
    MIC_OPUS_ENCODER_CONFIG
} from "../../../web/js/audio/mic-format.mjs";

function deferred() {
    let resolve, reject;
    const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
    return {promise, resolve, reject};
}

async function opusFixture(t) {
    const globals = ["WebSocket", "window", "location", "AudioEncoder", "AudioData"];
    const originals = globals.map(key => Object.getOwnPropertyDescriptor(globalThis, key));
    let clock = 0, micHandler, txEnabled;
    class Socket {
        static OPEN = 1;
        static latest;
        constructor() { this.readyState = 0; this.bufferedAmount = 0; this.sent = []; Socket.latest = this; }
        send(value) { this.sent.push(value); }
        close() { this.readyState = 3; }
    }
    class Encoder {
        static async isConfigSupported(config) { return {supported: true, config}; }
        constructor(callbacks) {
            Object.assign(this, callbacks);
            this.state = "unconfigured";
            this.encodeQueueSize = 0;
            this.submitted = [];
            this.pending = [];
            this.clock = null;
        }
        configure() { this.state = "configured"; }
        encode(data) {
            this.clock ??= data.timestamp;
            this.submitted.push(data.timestamp);
            this.pending.push(data.timestamp);
        }
        emit() {
            this.pending.shift();
            this.output({timestamp: this.clock, duration: 20000, byteLength: 80, copyTo: target => target.fill(0)});
            this.clock += 20000;
        }
        close() { this.state = "closed"; }
    }
    globalThis.WebSocket = Socket;
    globalThis.window = {location: {href: "https://localhost/"}, isSecureContext: true};
    globalThis.location = {protocol: "https:"};
    globalThis.AudioEncoder = Encoder;
    globalThis.AudioData = class { constructor(data) { Object.assign(this, data); } close() {} };
    const controller = {
        onMicData: handler => { micHandler = handler; },
        setMicTransmissionEnabled: enabled => { txEnabled = enabled; },
        getAudioRuntime: () => ({sampleRate: 48000, workletSupported: true}),
        getAudioContextTimeUs: () => clock,
        resetAudioState() { txEnabled = false; }
    };
    const client = new SvgWebSocket(controller);
    t.after(() => {
        client.disconnect();
        globals.forEach((key, i) => {
            if (originals[i]) Object.defineProperty(globalThis, key, originals[i]);
            else delete globalThis[key];
        });
    });
    client.initWebSocket();
    const open = () => {
        client.connect("test-player", "test-password", () => {});
        const socket = Socket.latest;
        socket.readyState = Socket.OPEN;
        socket.onopen();
        client.lastCapabilities = {type: "capabilities", audio: {micFormats: ["opus", "pcm16le"]}};
        return socket;
    };
    const ack = format => Socket.latest.onmessage({data: JSON.stringify({type: "capabilities_ack", selectedMicFormat: format})});
    open();
    await ack("opus");
    return {
        client, ack, open,
        message: data => Socket.latest.onmessage({data: JSON.stringify(data)}),
        reconnect: () => {
            let retry;
            const originalTimer = globalThis.setTimeout;
            try {
                globalThis.setTimeout = callback => { retry = callback; return 1; };
                Socket.latest.readyState = 3;
                Socket.latest.onclose({code: 1006, reason: "network drop"});
            } finally {
                globalThis.setTimeout = originalTimer;
            }
            assert.equal(typeof retry, "function");
            retry();
            Socket.latest.readyState = Socket.OPEN;
            Socket.latest.onopen();
        },
        get socket() { return Socket.latest; },
        enabled: () => txEnabled,
        setClock: value => { clock = value; },
        frame: (timestamp, emit = true) => {
            clock = timestamp + 20000;
            micHandler({samples: new Float32Array(960), timestamp});
            if (emit && client.micEncoder?.pending.length) client.micEncoder.emit();
        },
        packets: () => Socket.latest.sent.filter(value => typeof value !== "string")
    };
}

test("automatic reconnect renegotiates PCM and Opus before resuming microphone packets", async t => {
    for (const format of ["pcm16le", "opus"]) {
        await t.test(format, async t => {
            const f = await opusFixture(t);
            let statuses = 0;
            f.client.addEventListener("statusChange", () => statuses++);
            const connected = {type: "status", message: "Connected as test-player"};
            await f.message(connected);
            await f.ack(format);
            f.frame(1000000);
            assert.equal(f.packets().length, 1);
            f.reconnect();
            assert.equal(f.enabled(), false);
            f.frame(2000000);
            assert.equal(f.packets().length, 0);
            await f.message(connected);
            await f.message(connected);
            const capabilities = f.socket.sent.filter(value => typeof value === "string")
                .map(value => JSON.parse(value)).filter(value => value.type === "capabilities");
            assert.equal(capabilities.length, 1);
            assert.equal(f.enabled(), false);
            f.frame(2020000);
            assert.equal(f.packets().length, 0);
            await f.ack(format);
            f.frame(2040000);
            assert.equal(f.packets().length, 1);
            assert.ok(statuses >= 4);
            assert.equal(JSON.parse(f.socket.sent[0]).username, "test-player");
        });
    }
});

test("obsolete capability probes and acknowledgements cannot alter a replacement encoder", async t => {
    for (const operation of ["capabilities", "ack"]) {
        for (const fails of [false, true]) {
            await t.test(`${operation}, rejects=${fails}`, async t => {
                const f = await opusFixture(t);
                const pending = deferred();
                const encoderClass = globalThis.AudioEncoder;
                const originalSupport = encoderClass.isConfigSupported;
                let entered = false;
                encoderClass.isConfigSupported = () => { entered = true; return pending.promise; };
                t.after(() => { encoderClass.isConfigSupported = originalSupport; });
                const connected = {type: "status", message: "Connected as test-player"};
                if (operation === "ack") {
                    await f.message({type: "status", message: "Changing server"});
                }
                const oldSocket = f.socket;
                const oldOperation = operation === "ack" ? f.ack("opus") : f.message(connected);
                for (let i = 0; i < 20 && !entered; i++) await Promise.resolve();
                assert.equal(entered, true);
                f.reconnect();
                encoderClass.isConfigSupported = originalSupport;
                await f.message(connected);
                await f.ack("opus");
                const replacement = f.client.micEncoder;
                assert.ok(replacement);
                if (fails) pending.reject(new Error("old probe failed"));
                else pending.resolve({supported: true, config: MIC_OPUS_ENCODER_CONFIG});
                await oldOperation;
                assert.equal(f.client.micEncoder, replacement);
                assert.equal(replacement.state, "configured");
                assert.equal(f.enabled(), true);
                const sent = f.socket.sent.length;
                await oldSocket.onmessage({data: JSON.stringify({type: "status", message: "Changing server"})});
                assert.equal(f.enabled(), true);
                assert.equal(f.socket.sent.length, sent);
                f.frame(2000000);
                assert.equal(f.packets().length, 1);
            });
        }
    }
});

test("Opus freshness follows capture time across long and repeated short gaps", async t => {
    for (const gap of [1000000, 80000]) {
        await t.test(`gap=${gap / 1000}ms`, async t => {
            const f = await opusFixture(t);
            let timestamp = 1000000;
            for (let burst = 0; burst < 5; burst++) {
                for (let i = 0; i < 10; i++) { f.frame(timestamp); timestamp += 20000; }
                timestamp += gap;
            }
            assert.equal(f.packets().length, 50);
        });
    }
});

test("Opus resumes after network and encoder backpressure skip input frames", async t => {
    const f = await opusFixture(t);
    f.frame(1000000);
    f.socket.bufferedAmount = 641;
    f.frame(1020000);
    f.socket.bufferedAmount = 0;
    f.client.micEncoder.encodeQueueSize = 2;
    f.frame(1040000);
    f.client.micEncoder.encodeQueueSize = 0;
    f.frame(2000000);
    assert.deepEqual(f.client.micEncoder.submitted, [1000000, 2000000]);
    assert.equal(f.packets().length, 2);
});

test("Opus drops stale capture and output while draining metadata for later speech", async t => {
    const f = await opusFixture(t);
    f.frame(1000000, false);
    f.setClock(1200000);
    f.client.micEncoder.emit();
    assert.equal(f.packets().length, 0);
    f.frame(2000000);
    assert.equal(f.packets().length, 1);
    f.socket.bufferedAmount = 641;
    f.frame(2020000);
    f.socket.bufferedAmount = 0;
    f.frame(2040000, false);
    f.socket.bufferedAmount = 641;
    f.client.micEncoder.emit();
    f.socket.bufferedAmount = 0;
    f.frame(3000000);
    assert.equal(f.packets().length, 2);
    // A stale input must never reach the encoder.
    const before = f.client.micEncoder.submitted.length;
    // frame() advances the clock; keep the clock getter deliberately ahead.
    f.client.audioController.getAudioContextTimeUs = () => 4000000;
    f.frame(3500000);
    assert.equal(f.client.micEncoder.submitted.length, before);
});

test("Opus pending capture metadata is bounded even after the encoder queue drains", async t => {
    const f = await opusFixture(t);
    for (const timestamp of [1000000, 1020000, 1040000]) f.frame(timestamp, false);
    assert.equal(f.client.micEncoder.submitted.length, 2);
    f.client.micEncoder.emit();
    f.client.micEncoder.emit();
    f.frame(2000000);
    assert.equal(f.packets().length, 3);
});

test("Opus metadata belongs to its encoder across transfer, reconnect, and fallback", async t => {
    const f = await opusFixture(t);
    for (const transition of ["transfer", "reconnect", "fallback", "close"]) {
        f.frame(1000000, false);
        const old = f.client.micEncoder;
        if (transition === "transfer") {
            await f.socket.onmessage({data: JSON.stringify({type: "status", message: "Changing server"})});
            await f.socket.onmessage({data: JSON.stringify({type: "status", message: "Connected as test-player"})});
        } else if (transition === "reconnect") {
            f.open();
        } else if (transition === "close") {
            const closing = f.socket;
            f.client.disconnect();
            closing.onclose({code: 1000, reason: "test shutdown"});
            f.open();
        } else {
            old.error(new Error("runtime encoder error"));
            await f.ack("pcm16le");
            assert.equal(f.enabled(), true);
        }
        await f.ack("opus");
        const current = f.client.micEncoder;
        assert.notEqual(current, old);
        old.emit();
        old.error(new Error("late error from replaced encoder"));
        assert.equal(f.client.micEncoder, current);
        assert.equal(f.enabled(), true);
        const before = f.packets().length;
        f.frame(2000000);
        assert.equal(f.packets().length, before + 1);
    }
});

test("unmatched Opus output requests acknowledged PCM fallback", async t => {
    const f = await opusFixture(t);
    f.client.micEncoder.output({timestamp: 0, duration: 20000, byteLength: 80, copyTo: target => target.fill(0)});
    assert.equal(f.enabled(), false);
    assert.equal(f.client.micEncoder, null);
    assert.deepEqual(f.client.lastCapabilities.audio.micFormats, ["pcm16le"]);
    await f.ack("pcm16le");
    f.frame(1000000);
    assert.equal(f.packets().at(-1).byteLength, 1920);
});

function captureFixture(t) {
    const originals = new Map(["navigator", "AudioWorkletNode"].map(
        name => [name, Object.getOwnPropertyDescriptor(globalThis, name)]
    ));
    t.after(() => {
        for (const [name, descriptor] of originals) {
            if (descriptor) Object.defineProperty(globalThis, name, descriptor);
            else delete globalThis[name];
        }
    });
    const nodes = [], sources = [], streams = [], requests = [];
    const queue = [];
    const newStream = () => {
        const track = {stopped: false, stop() { this.stopped = true; }};
        const stream = {getTracks: () => [track]};
        streams.push(stream);
        return stream;
    };
    Object.defineProperty(globalThis, "navigator", {
        configurable: true,
        value: {mediaDevices: {getUserMedia: (constraints) => {
            requests.push(constraints);
            return queue.length ? queue.shift().promise : Promise.resolve(newStream());
        }}}
    });
    globalThis.AudioWorkletNode = class {
        constructor() { this.port = {onmessage: null}; this.disconnected = false; nodes.push(this); }
        disconnect() { this.disconnected = true; }
    };
    const audio = new SvgAudio();
    t.after(() => audio.stopMic());
    audio.audioRuntime.canCaptureMic = true;
    audio.audioContext = {
        resume: async () => {},
        createMediaStreamSource: () => {
            const source = {disconnected: false, connect() {}, disconnect() { this.disconnected = true; }};
            sources.push(source);
            return source;
        }
    };
    const holdPermission = () => { const pending = deferred(); queue.push(pending); return pending; };
    return {audio, nodes, sources, streams, requests, newStream, holdPermission};
}

test("repeated connected notifications reuse capture and send one frame per interval", async t => {
    const {audio, nodes, requests} = captureFixture(t);
    let frames = 0;
    audio.onMicData(() => frames++);
    audio.setMicTransmissionEnabled(true);
    // The integration UI starts capture on socket open and again on authenticated status.
    await audio.startMic("test-device");
    await audio.startMic("test-device");
    for (const node of nodes) {
        node.port.onmessage?.({data: {samples: new Float32Array(960), speech: true, timestamp: 0}});
    }
    assert.equal(frames, 1);
    assert.equal(requests.length, 1);
    assert.equal(nodes.length, 1);
});

test("concurrent starts share one pending capture", async t => {
    const {audio, nodes, requests, newStream, holdPermission} = captureFixture(t);
    const pending = holdPermission();
    const first = audio.startMic(), second = audio.startMic();
    await Promise.resolve();
    pending.resolve(newStream());
    await Promise.all([first, second]);
    assert.equal(requests.length, 1);
    assert.equal(nodes.length, 1);
});

test("stop releases a late permission result without creating capture", async t => {
    const {audio, nodes, newStream, holdPermission} = captureFixture(t);
    const pending = holdPermission();
    const starting = audio.startMic();
    await Promise.resolve();
    audio.stopMic();
    const lateStream = newStream();
    pending.resolve(lateStream);
    await starting;
    assert.ok(lateStream.getTracks()[0].stopped);
    assert.equal(nodes.length, 0);
    assert.equal(audio.microphoneStream, null);
});

test("late results and failures cannot replace or stop a newer capture", async t => {
    const {audio, nodes, requests, newStream, holdPermission} = captureFixture(t);
    for (const failOld of [false, true]) {
        const pending = holdPermission();
        const oldStart = audio.startMic();
        await Promise.resolve();
        audio.stopMic();
        await audio.startMic("new-device");
        const currentStream = audio.microphoneStream;
        const currentNode = audio.micNode;
        if (failOld) {
            const rejected = assert.rejects(oldStart, /old permission failed/);
            pending.reject(new Error("old permission failed"));
            await rejected;
        } else {
            const lateStream = newStream();
            pending.resolve(lateStream);
            await oldStart;
            assert.ok(lateStream.getTracks()[0].stopped);
        }
        assert.equal(audio.microphoneStream, currentStream);
        assert.equal(audio.micNode, currentNode);
        assert.equal(currentStream.getTracks()[0].stopped, false);
        await audio.startMic();
        assert.equal(audio.micNode, currentNode);
        audio.stopMic();
    }
    assert.equal(requests.length, 4);
    assert.equal(nodes.length, 2);
});

test("failed permission or node startup releases resources and allows retry", async t => {
    const {audio, nodes, sources, streams, holdPermission} = captureFixture(t);
    const pending = holdPermission();
    const denied = assert.rejects(audio.startMic(), /permission denied/);
    await Promise.resolve();
    pending.reject(new Error("permission denied"));
    await denied;
    const Node = globalThis.AudioWorkletNode;
    globalThis.AudioWorkletNode = class { constructor() { throw new Error("node failed"); } };
    await assert.rejects(audio.startMic(), /node failed/);
    assert.ok(streams[0].getTracks()[0].stopped);
    assert.ok(sources[0].disconnected);
    globalThis.AudioWorkletNode = Node;
    await audio.startMic();
    assert.equal(nodes.length, 1);
    assert.equal(audio.microphoneStream.getTracks()[0].stopped, false);
});

test("stop/reset clean capture, ignore stale callbacks, and preserve only the device-switch gate", async t => {
    const {audio, nodes, sources, streams} = captureFixture(t);
    let frames = 0;
    audio.onMicData(() => frames++);
    audio.setMicTransmissionEnabled(true);
    await audio.startMic();
    const staleCallback = nodes[0].port.onmessage;
    audio.stopMic();
    staleCallback({data: {samples: new Float32Array(960), speech: true, timestamp: 0}});
    assert.equal(frames, 0);
    assert.equal(nodes[0].port.onmessage, null);
    assert.ok(nodes[0].disconnected && sources[0].disconnected && streams[0].getTracks()[0].stopped);
    assert.equal(audio.micTransmissionEnabled, true);
    await audio.startMic("replacement-device");
    assert.equal(audio.micTransmissionEnabled, true);
    audio.resetAudioState();
    assert.equal(audio.micTransmissionEnabled, false);
    assert.ok(nodes[1].disconnected && sources[1].disconnected && streams[1].getTracks()[0].stopped);
    assert.equal(audio.micNode, null);
    assert.equal(audio.micSource, null);
    assert.equal(audio.microphoneStream, null);
});

test("worklet emits only exact 960-sample 20 ms frames", async () => {
    let Processor;
    let frame = 0;
    const posted = [];
    class AudioWorkletProcessor {
        constructor() {
            this.port = {postMessage: (message) => posted.push(message)};
        }
    }
    const source = await readFile(new URL("../../../web/js/audio/microphone.js", import.meta.url), "utf8");
    let currentFrame = 0;
    const processorContext = vm.createContext({
        AudioWorkletProcessor,
        Float32Array,
        get currentFrame() { return currentFrame; },
        sampleRate: 48000,
        registerProcessor: (_name, value) => { Processor = value; }
    });
    vm.runInContext(source, processorContext);
    const processor = new Processor();
    for (let packet = 0; packet < 2; packet++) {
        for (const length of [128, 128, 128, 128, 128, 128, 128, 64]) {
            currentFrame = frame;
            processor.process([[new Float32Array(length).fill(0.02)]]);
            frame += length;
        }
    }
    assert.equal(posted.length, 2);
    assert.deepEqual(Array.from(posted, value => value.samples.length), [960, 960]);
    assert.deepEqual(Array.from(posted, value => value.timestamp), [0, 20000]);
    assert.ok(posted.every(value => value.speech));
});

test("mic format negotiation defaults old acknowledgements and unsupported encoders to PCM", () => {
    assert.equal(micFormatFromAck(undefined), "pcm16le");
    assert.equal(micFormatFromAck("unexpected"), "pcm16le");
    assert.equal(micFormatFromAck("opus"), "opus");
    assert.deepEqual(micFormatsForEncoder(48000, true), ["opus", "pcm16le"]);
    assert.deepEqual(micFormatsForEncoder(44100, true), ["pcm16le"]);
    assert.deepEqual(micFormatsForEncoder(48000, false), ["pcm16le"]);
});

test("stale capture frames are dropped by the age bound", () => {
    assert.equal(isMicFrameFresh(100000, 200000, 150), true);
    assert.equal(isMicFrameFresh(100000, 251000, 150), false);
    assert.equal(isMicFrameFresh(200000, 100000, 150), false);
});

test("unsupported Opus durations and packet lengths are rejected", () => {
    assert.equal(isSupportedOpusChunk({byteLength: 80, duration: 20000}), true);
    assert.equal(isSupportedOpusChunk({byteLength: 80, duration: null}), true);
    assert.equal(isSupportedOpusChunk({byteLength: 80, duration: 10000}), false);
    assert.equal(isSupportedOpusChunk({byteLength: 0, duration: 20000}), false);
    assert.equal(isSupportedOpusChunk({byteLength: 1276, duration: 20000}), false);
});

test("SvgAudio forwards the worklet capture timestamp to its mic callback", async () => {
    const originalNavigator = Object.getOwnPropertyDescriptor(globalThis, "navigator");
    const originalWorkletNode = Object.getOwnPropertyDescriptor(globalThis, "AudioWorkletNode");
    const instances = [];
    class FakeAudioWorkletNode {
        constructor() {
            this.port = {onmessage: null};
            instances.push(this);
        }
        connect() {}
        disconnect() {}
    }
    Object.defineProperty(globalThis, "navigator", {
        configurable: true,
        value: {mediaDevices: {getUserMedia: async () => ({getTracks: () => []})}}
    });
    globalThis.AudioWorkletNode = FakeAudioWorkletNode;
    try {
        const {SvgAudio} = await import("../../../web/js/audio/audio.js");
        const audio = new SvgAudio();
        audio.audioRuntime.canCaptureMic = true;
        audio.audioContext = {
            resume: async () => {},
            createMediaStreamSource: () => ({connect() {}, disconnect() {}})
        };
        audio.setMicTransmissionEnabled(true);
        let forwarded;
        audio.onMicData(frame => { forwarded = frame; });
        await audio.startMic();

        const samples = new Float32Array(960);
        instances[0].port.onmessage({data: {samples, speech: true, timestamp: 123456}});
        assert.equal(forwarded.samples, samples);
        assert.equal(forwarded.timestamp, 123456);
        audio.stopMic();
        assert.equal(audio.micTransmissionEnabled, true, "changing devices must retain the acknowledged format");
        await audio.startMic();
        instances[1].port.onmessage({data: {samples, speech: true, timestamp: 143456}});
        assert.equal(forwarded.timestamp, 143456);
        audio.stopMic();
    } finally {
        if (originalNavigator) Object.defineProperty(globalThis, "navigator", originalNavigator);
        else delete globalThis.navigator;
        if (originalWorkletNode) Object.defineProperty(globalThis, "AudioWorkletNode", originalWorkletNode);
        else delete globalThis.AudioWorkletNode;
    }
});

test("transfer status pauses TX and Opus is configured for raw 20 ms packets", () => {
    assert.equal(isBackendTransferStatus("Changing server"), true);
    assert.equal(isBackendTransferStatus("changingServer"), true);
    assert.equal(isBackendTransferStatus("Connected as Player"), false);
    assert.equal(MIC_OPUS_ENCODER_CONFIG.opus.format, "opus");
    assert.equal(MIC_OPUS_ENCODER_CONFIG.opus.frameDuration, 20000);
    assert.equal(MIC_OPUS_ENCODER_CONFIG.sampleRate, 48000);
    assert.equal(MIC_OPUS_ENCODER_CONFIG.numberOfChannels, 1);
});

test("mic TX waits for ack, treats old ack as PCM, and renegotiates when Opus cannot initialize", async () => {
    const originalGlobals = {
        WebSocket: globalThis.WebSocket,
        window: globalThis.window,
        location: globalThis.location,
        AudioEncoder: globalThis.AudioEncoder,
        AudioData: globalThis.AudioData
    };
    class FakeWebSocket {
        static OPEN = 1;
        static latest;
        constructor(url) {
            this.url = url;
            this.readyState = 0;
            this.bufferedAmount = 0;
            this.sent = [];
            FakeWebSocket.latest = this;
        }
        send(value) { this.sent.push(value); }
        close() { this.readyState = 3; }
    }
    let txEnabled = false;
    globalThis.WebSocket = FakeWebSocket;
    globalThis.window = {href: "http://localhost/", location: {href: "http://localhost/"}, isSecureContext: false};
    globalThis.location = {protocol: "http:"};
    delete globalThis.AudioEncoder;
    delete globalThis.AudioData;

    try {
        const {SvgWebSocket} = await import("../../../web/js/websocket.js");
        const controller = {
            setMicTransmissionEnabled: (enabled) => { txEnabled = enabled; },
            getAudioRuntime: () => ({sampleRate: 48000, workletSupported: true}),
            getAudioContextTimeUs: () => 0,
            resetAudioState() {}
        };
        const client = new SvgWebSocket(controller);
        client.connect("player", "secret", () => {});
        const socket = FakeWebSocket.latest;
        socket.readyState = FakeWebSocket.OPEN;
        socket.onopen();
        client.lastCapabilities = {type: "capabilities", audio: {protocols: ["legacy"], micFormats: ["opus"]}};

        await socket.onmessage({data: JSON.stringify({type: "capabilities_ack"})});
        assert.equal(client.selectedMicFormat, "pcm16le");
        assert.equal(txEnabled, true);

        await socket.onmessage({data: JSON.stringify({type: "status", message: "Changing server"})});
        assert.equal(txEnabled, false);
        await socket.onmessage({data: JSON.stringify({type: "capabilities_ack", selectedMicFormat: "opus"})});
        assert.equal(txEnabled, false);
        assert.deepEqual(client.lastCapabilities.audio.micFormats, ["pcm16le"]);

        await socket.onmessage({data: JSON.stringify({type: "capabilities_ack", selectedMicFormat: "pcm16le"})});
        assert.equal(client.selectedMicFormat, "pcm16le");
        assert.equal(txEnabled, true);

        class FakeAudioEncoder {
            static instances = [];
            static async isConfigSupported(config) { return {supported: true, config}; }
            constructor(callbacks) {
                Object.assign(this, callbacks);
                this.state = "unconfigured";
                this.encodeQueueSize = 0;
                FakeAudioEncoder.instances.push(this);
            }
            configure(config) { this.config = config; this.state = "configured"; }
            close() { this.state = "closed"; }
        }
        globalThis.AudioEncoder = FakeAudioEncoder;
        globalThis.AudioData = class {};
        await socket.onmessage({data: JSON.stringify({type: "status", message: "Changing server"})});
        await socket.onmessage({data: JSON.stringify({type: "capabilities_ack", selectedMicFormat: "opus"})});
        assert.equal(txEnabled, true);
        assert.equal(FakeAudioEncoder.instances.at(-1).config.opus.frameDuration, 20000);
        FakeAudioEncoder.instances.at(-1).error(new Error("encoder runtime failure"));
        assert.equal(txEnabled, false);
        assert.deepEqual(client.lastCapabilities.audio.micFormats, ["pcm16le"]);

        await socket.onmessage({data: JSON.stringify({type: "capabilities_ack", selectedMicFormat: "pcm16le"})});
        assert.equal(client.selectedMicFormat, "pcm16le");
        assert.equal(txEnabled, true);

        await socket.onmessage({data: JSON.stringify({type: "status", message: "Changing server"})});
        await socket.onmessage({data: JSON.stringify({type: "capabilities_ack", selectedMicFormat: "opus"})});
        assert.equal(txEnabled, true);
        FakeAudioEncoder.instances.at(-1).output({byteLength: 1276, duration: 20000, timestamp: 0});
        assert.equal(txEnabled, false);
        assert.deepEqual(client.lastCapabilities.audio.micFormats, ["pcm16le"]);
        await socket.onmessage({data: JSON.stringify({type: "capabilities_ack", selectedMicFormat: "pcm16le"})});
        assert.equal(txEnabled, true);
    } finally {
        for (const [key, value] of Object.entries(originalGlobals)) {
            if (value === undefined) delete globalThis[key];
            else globalThis[key] = value;
        }
    }
});
