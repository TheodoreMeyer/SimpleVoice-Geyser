---
title: Server Protocol Reference
layout: projects
project: simplevoicegeyser
---

# Protocol Reference

This page documents the 0.1.2 websocket client/server packet flow. It covers the browser web UI and native Android app protocol used by Simple Voice Geyser, not the internal Simple Voice Chat protocol.

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

## Join Form And Join Packets

The browser web UI and Android app both collect:

| Field      | Meaning                                                  |
|------------|----------------------------------------------------------|
| `username` | Bedrock username or server-recognized player name        |
| `password` | Voice password configured through `/svg pswd [password]` |

Compatibility validation runs before username/password authentication.

### Browser Join

Browser assets are served by the plugin build, so browser joins must include the exact generated build id:

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "build": "<SvgCore.BUILD_ID>"
}
```

When `client` is absent, the server uses browser compatibility policy and validates `build` exactly against `SvgCore.BUILD_ID`.

### Android Join

Native Android app releases are independent from plugin commit hashes, so Android joins identify a stable native app protocol instead of a browser build id:

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "client": {
    "kind": "android",
    "version": "1.0.0",
    "protocol": 1
  }
}
```

Android client fields:

| Field             | Meaning                                           | Validation                                    |
|-------------------|---------------------------------------------------|-----------------------------------------------|
| `client.kind`     | Selects the native client compatibility policy.   | Required string, currently only `android`.    |
| `client.version`  | Human-readable app version for diagnostics.       | Required string, trimmed value must not empty. |
| `client.protocol` | Stable native websocket/application protocol.     | Required integer, currently only `1`.         |

Native protocol `1` is independent from `svg-v2` audio framing. Native protocol `1` describes the app/server websocket contract as a whole; `svg-v2` is only one negotiated server-to-client audio transport after authentication.

If an Android join includes an unnecessary `build`, the `client` object is authoritative and the browser build value is ignored for compatibility.

Validation order:

| Condition                                             | Result                                                                 |
|-------------------------------------------------------|------------------------------------------------------------------------|
| No `client` object                                    | Run browser `build` validation.                                        |
| Missing or outdated browser `build`                   | Server sends an error and closes with `4008` / `update_required`.      |
| Android metadata valid with protocol `1`              | Compatibility succeeds and authentication runs next.                   |
| `client` is not an object                             | Server sends an error and closes with `4005` / `invalid_client_info`.  |
| Missing/blank `client.kind`                           | Server sends an error and closes with `4005` / `invalid_client_info`.  |
| Unknown `client.kind`                                 | Server sends an error and closes with `4005` / `unsupported_client_type`. |
| Missing/blank/non-string Android `client.version`     | Server sends an error and closes with `4005` / `invalid_client_info`.  |
| Missing or non-integer Android `client.protocol`      | Server sends an error and closes with `4005` / `invalid_client_info`.  |
| Valid Android metadata with unsupported protocol      | Server sends an error and closes with `4008` / `app_protocol_unsupported`. |
| Invalid username/password                             | Server sends an error after compatibility succeeds.                    |
| Player is not allowed to use the web voice bridge     | Server sends an error after compatibility succeeds.                    |
| Successful authentication                             | Server creates the voice bridge connection and sends a status message. |

The server stores validated client identity for diagnostics, but it must not log passwords or complete join packets.

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

Known close reasons used by compatibility validation:

| Code   | Reason                       | Meaning                                                   |
|--------|------------------------------|-----------------------------------------------------------|
| `4005` | `invalid_client_info`        | Native client metadata is malformed or incomplete.        |
| `4005` | `unsupported_client_type`    | The `client.kind` value is not supported by this plugin.  |
| `4008` | `update_required`            | Browser build id is missing or stale.                     |
| `4008` | `app_protocol_unsupported`   | Android native protocol is valid but unsupported.         |

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

## Local Manual Testing Checklist

- Browser with matching `SvgCore.BUILD_ID` joins successfully.
- Browser with missing or stale `build` is rejected with `4008` / `update_required`.
- Android join with `client.kind = "android"`, nonblank `version`, and `protocol = 1` reaches normal authentication without `build`.
- Android wrong password follows the existing authentication failure path.
- Android unsupported protocol receives `4008` / `app_protocol_unsupported`.
- Malformed Android metadata receives `4005` and does not reach authentication.
- Authenticated Android client sends `capabilities` and receives `capabilities_ack`.
- Android chat send/receive works after authentication.
- Android microphone audio still sends PCM16LE frames after join.
- Server-to-client legacy and `svg-v2` audio behavior remain unchanged.
