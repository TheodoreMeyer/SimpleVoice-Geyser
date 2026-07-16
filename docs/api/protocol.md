---
title: Websocket Protocol
layout: projects
project: simplevoicegeyser
---

# Protocol Reference

This page documents the 0.1.2-3 websocket client/server packet flow. It covers the browser web UI and native Android app protocol used by Simple Voice Geyser, not the internal Simple Voice Chat protocol.

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

### Web Join

Browser assets are served by the plugin build, so web joins identify the built-in client and include the exact generated build id:

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "clientType": {
    "type": "Web",
    "serverVersion": "<SvgCore.VERSION>",
    "serverBuild": "<SvgCore.BUILD_ID>"
  }
}
```

For transition compatibility, legacy browser joins with top-level `build` are still accepted:

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "build": "<SvgCore.BUILD_ID>"
}
```

In both forms, the browser build value is validated exactly against `SvgCore.BUILD_ID`.

### Svg-App Join

Native Android app releases are independent from plugin commit hashes, so Svg-App joins target the project/server version instead of the browser build id:

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "clientType": {
    "type": "Svg-App",
    "serverVersion": "<SvgCore.VERSION>"
  }
}
```

Client type fields:

| Field                      | Meaning                                      | Validation                                          |
|----------------------------|----------------------------------------------|-----------------------------------------------------|
| `clientType.type`          | Client family identifier.                    | Required string. Supported values: `Web`, `Svg-App`. |
| `clientType.serverVersion` | Project/server version targeted by client.  | Required string for structured client joins.        |
| `clientType.serverBuild`   | Browser build id targeted by built-in Web.   | Required for `Web`; optional for `Svg-App`.          |

`clientType` is independent from `svg-v2` audio framing. It describes the app/server websocket compatibility contract as a whole; `svg-v2` is only one negotiated server-to-client audio transport after authentication.

### Client Type Filtering

Server owners can optionally block or allow client types:

```yaml
client:
  allowedTypes:
    isBlackList: true
    list: []
```

When `isBlackList` is `true`, listed client types are rejected. When `false`, only listed client types are accepted. The default blocks nothing.

Validation order:

| Condition                                            | Result                                                                 |
|------------------------------------------------------|------------------------------------------------------------------------|
| No `clientType` and no old `client` object            | Run legacy browser `build` validation.                                 |
| Missing or outdated browser build                    | Server sends an error and closes with `4008` / `update_required`.      |
| `clientType` is not an object                        | Server sends an error and closes with `4005` / `invalid_client_info`.  |
| Missing or blank `clientType.type`                   | Server sends an error and closes with `4005` / `invalid_client_info`.  |
| Unknown or config-blocked `clientType.type`          | Server sends an error and closes with `4005` / `unsupported_client_type`. |
| `Web` has missing or outdated `serverBuild`          | Server sends an error and closes with `4008` / `update_required`.      |
| `Svg-App` has missing or mismatched `serverVersion`  | Server sends an error and closes with `4008` / `app_protocol_unsupported`. |
| Old Android `client.kind/version/protocol` metadata  | Server sends an error and closes with `4005` / `invalid_client_info`.  |
| Invalid username/password                            | Server sends an error after compatibility succeeds.                    |
| Player is not allowed to use the web voice bridge    | Server sends an error after compatibility succeeds.                    |
| Successful authentication                            | Server creates the voice bridge connection and sends a status message. |

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
| `4005` | `invalid_client_info`        | Client type metadata is malformed or incomplete.          |
| `4005` | `unsupported_client_type`    | The client type is unknown or blocked by config.          |
| `4008` | `update_required`            | Browser build id is missing or stale.                     |
| `4008` | `app_protocol_unsupported`   | Svg-App targets an unsupported server version.            |

## Client To Server Audio

The 0.1.2-3 web client sends microphone audio as binary websocket frames after authentication.

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

- Web join with matching `clientType.serverBuild` joins successfully.
- Legacy browser join with matching top-level `build` still joins successfully.
- Web join with missing or stale build is rejected with `4008` / `update_required`.
- Svg-App join with `clientType.type = "Svg-App"` and matching `serverVersion` reaches normal authentication without browser build.
- Svg-App wrong password follows the existing authentication failure path.
- Svg-App server-version mismatch receives `4008` / `app_protocol_unsupported`.
- Malformed or old Android metadata receives `4005` and does not reach authentication.
- Client type blacklist and whitelist modes reject the expected client types before authentication.
- Authenticated Svg-App client sends `capabilities` and receives `capabilities_ack`.
- Svg-App chat send/receive works after authentication.
- Svg-App microphone audio still sends PCM16LE frames after join unless a later app version implements `svg-v2` send/receive.
- Server-to-client legacy and `svg-v2` audio behavior remain unchanged.
