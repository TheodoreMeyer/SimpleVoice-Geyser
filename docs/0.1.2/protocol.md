---
title: Server Protocol Reference
layout: projects
project: simplevoicegeyser
---

# Protocol Reference

This page documents the 0.1.2 web client/server packet flow. It covers the browser web UI protocol used by Simple Voice Geyser, not the internal Simple Voice Chat protocol.

## Connection

The browser connects to the websocket endpoint relative to the loaded web UI path:

```text
ws://host[:port]/ws
wss://host[:port]/ws
```

When the web UI is served from a subpath, the websocket URL is resolved relative to that subpath.

Examples:

| Web UI URL                   | Websocket URL                |
|------------------------------|------------------------------|
| `https://voice.example.com/` | `wss://voice.example.com/ws` |
| `https://example.com/svg/`   | `wss://example.com/svg/ws`   |

Use `wss://` for public deployments. Browsers may restrict microphone access on insecure contexts.

## Message Types

The protocol uses two websocket message classes:

| Direction        | Message class | Purpose                                                   |
|------------------|---------------|-----------------------------------------------------------|
| Client to server | JSON text     | Join, capabilities, chat                                  |
| Server to client | JSON text     | Status, errors, chat, capabilities acknowledgement        |
| Client to server | Binary        | Microphone audio                                          |
| Server to client | Binary        | Receive audio, either legacy PCM or `svg-v2` framed audio |

## Join Form And Join Packet

The web UI join form collects:

| Field      | Meaning                                                  |
|------------|----------------------------------------------------------|
| `username` | Bedrock username or server-recognized player name        |
| `password` | Voice password configured through `/svg pswd [password]` |

On websocket open, the client sends:

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "build": "client-build-id"
}
```

Server behavior:

| Condition                                         | Result                                                                 |
|---------------------------------------------------|------------------------------------------------------------------------|
| Missing or outdated `build`                       | Server sends an error and closes with the outdated-client close code.  |
| Invalid username/password                         | Server sends an error.                                                 |
| Player is not allowed to use the web voice bridge | Server sends an error.                                                 |
| Successful authentication                         | Server creates the voice bridge connection and sends a status message. |

Successful join response:

```json
{
  "type": "status",
  "message": "Connected as PlayerName.",
  "fatal": false
}
```

After this status message, the current browser client sends its audio capability packet.

## Capability Packet

The capability packet lets the server choose `legacy` or `svg-v2` per websocket session.

Client to server:

```json
{
  "type": "capabilities",
  "audio": {
    "protocols": ["legacy", "svg-v2"],
    "supportsOpusDecoder": true,
    "secureContext": true,
    "decoder": {
      "opusWasm": true,
      "webCodecs": false,
      "wasmError": null,
      "webCodecsError": "disabled_by_policy_wasm_only"
    }
  }
}
```

Notes:

| Field                       | Meaning                                                                               |
|-----------------------------|---------------------------------------------------------------------------------------|
| `audio.protocols`           | Supported receive-audio transports. Unsupported/degraded browsers send only `legacy`. |
| `audio.supportsOpusDecoder` | Whether the client can decode Opus for `svg-v2` receive audio.                        |
| `audio.secureContext`       | Browser secure-context state.                                                         |
| `audio.decoder.opusWasm`    | Whether the browser WASM Opus decoder is ready.                                       |
| `audio.decoder.webCodecs`   | Reserved for future app/non-browser clients. The current browser path is WASM-only.   |

Server acknowledgement:

```json
{
  "type": "capabilities_ack",
  "selectedMode": "svg-v2",
  "fallbackCount": 0
}
```

`selectedMode` is either `legacy` or `svg-v2`.

If `server.audio.transport-mode` is `auto`, the server selects `svg-v2` only when the client reports compatible protocol and decoder support. Otherwise, it uses `legacy` when `server.audio.allow-legacy-fallback` is enabled.

## Chat Packet

Client to server:

```json
{
  "type": "chat",
  "message": "Hello from web chat"
}
```

Server behavior:

| Condition                                    | Result                                                                       |
|----------------------------------------------|------------------------------------------------------------------------------|
| Client is not authenticated                  | Server sends an error.                                                       |
| Message contains unsupported characters only | Server sends an error.                                                       |
| Message is valid                             | Server forwards sanitized chat and echoes a chat response to the web client. |

Server chat response:

```json
{
  "type": "chat",
  "message": "You: Hello from web chat",
  "fatal": false
}
```

## Server Status And Error Packets

Server text packets use this shape:

```json
{
  "type": "status",
  "message": "Human-readable message",
  "fatal": false
}
```

Supported text packet `type` values:

| Type               | Meaning                                   |
|--------------------|-------------------------------------------|
| `status`           | Normal state update.                      |
| `error`            | Recoverable or fatal error message.       |
| `chat`             | Chat message delivered to the web client. |
| `generic`          | Non-specific informational message.       |
| `capabilities_ack` | Audio transport selection response.       |

When `fatal` is `true`, the browser stops reconnecting automatically.

## Close Codes

|   Code | Meaning                                 |
|-------:|-----------------------------------------|
| `1001` | Generic close.                          |
| `4001` | Session replaced by another connection. |
| `4002` | Timeout close.                          |
| `4003` | Player left the game.                   |
| `4004` | Fatal error.                            |
| `4005` | Packet error.                           |
| `4006` | Server shutdown.                        |
| `4007` | Closed session.                         |
| `4008` | Outdated client.                        |

## Client To Server Audio

The 0.1.2 web client sends microphone audio as binary websocket frames after authentication.

Current packet shape:

| Field              | Value                           |
|--------------------|---------------------------------|
| Encoding           | PCM signed 16-bit little-endian |
| Channels           | Mono                            |
| Sample rate        | 48000 Hz                        |
| Samples per packet | 960                             |
| Bytes per packet   | 1920                            |

The server validates the packet as 960 PCM samples, encodes it to Opus through the Simple Voice Chat API, and forwards it into the voice chat connection.

Client mic Opus encoding is intentionally split into a future PR. It is not part of the 0.1.2 PR #45 protocol changes.

## Server To Client Legacy Audio

In `legacy` mode, server-to-client audio is a raw binary PCM frame without an envelope.

Packet shape:

| Field           | Value                                             |
|-----------------|---------------------------------------------------|
| Encoding        | PCM signed 16-bit little-endian                   |
| Channels        | Usually stereo after server-side spatial handling |
| Sample rate     | 48000 Hz                                          |
| Envelope/header | None                                              |

The browser treats non-`svg-v2` binary frames as legacy PCM.

## Server To Client `svg-v2` Audio

In `svg-v2` mode, server-to-client audio uses a versioned binary frame. Multibyte fields are little-endian.

| Field        |     Size | Description                                |
|--------------|---------:|--------------------------------------------|
| `magic`      |  2 bytes | ASCII `SV`                                 |
| `version`    |   1 byte | `2`                                        |
| `flags`      |   1 byte | Packet flags                               |
| `sequence`   |  4 bytes | Packet sequence number                     |
| `panQ15`     |  2 bytes | Signed Q15 pan metadata, `-1.0` to `1.0`   |
| `gainQ15`    |  2 bytes | Unsigned Q15 gain metadata, `0.0` to `1.0` |
| `sampleRate` |  2 bytes | Sample rate, normally `48000`              |
| `channels`   |   1 byte | Source channel count                       |
| `codec`      |   1 byte | `1=opus`, `2=pcm16le`                      |
| `payloadLen` |  4 bytes | Payload length                             |
| `payload`    | variable | Codec payload                              |

Known flag values:

| Flag                       |  Value | Meaning                                          |
|----------------------------|-------:|--------------------------------------------------|
| `FLAG_STATIC_PACKET`       | `0x01` | Source packet is static/non-positional.          |
| `FLAG_DISTANCE_ATTENUATED` | `0x02` | Server calculated distance attenuation metadata. |
| `FLAG_HAS_PAN`             | `0x04` | Frame includes pan metadata.                     |

The current `svg-v2` receive path usually sends Opus payloads with compact spatial metadata. The browser decodes the payload and applies pan/gain locally before enqueueing PCM into the speaker worklet.

## Compatibility Rules

| Client/server state                                            | Expected behavior                               |
|----------------------------------------------------------------|-------------------------------------------------|
| New client, `transport-mode=auto`, decoder ready               | Server selects `svg-v2`.                        |
| New client, decoder unavailable                                | Server uses `legacy` when fallback is enabled.  |
| Old client with no capability packet                           | Server remains on `legacy`.                     |
| `transport-mode=legacy`                                        | Server always uses `legacy`.                    |
| `transport-mode=svg-v2`, fallback disabled, unsupported client | No compatible receive-audio path is guaranteed. |
