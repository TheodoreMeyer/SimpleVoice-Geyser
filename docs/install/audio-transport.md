---
title: Audio Transport
layout: projects
project: simplevoicegeyser
---

# Audio Transport

Simple Voice Geyser can send audio to the web client using either the legacy transport or the newer `svg-v2` transport.

## Admin Summary

The default setting is:

```yaml
server:
  audio:
    transport-mode: auto
    allow-legacy-fallback: true
```

`auto` is the recommended transition mode. It lets capable clients use `svg-v2` while keeping older or unsupported clients on the legacy path.

## Transport Modes

| Mode     | Behavior                                                                                                                                     |
|----------|----------------------------------------------------------------------------------------------------------------------------------------------|
| `legacy` | Server decodes incoming Simple Voice Chat Opus audio, applies distance/pan handling, converts to PCM, and sends PCM audio to the web client. |
| `svg-v2` | Server sends the original Opus payload plus compact spatial metadata. The web client decodes and applies spatial gain/pan locally.           |
| `auto`   | Server chooses `svg-v2` only when the web client reports compatible support. Otherwise it falls back to `legacy` when fallback is allowed.   |

## Fallback Behavior

`server.audio.allow-legacy-fallback` controls whether the server can use `legacy` when `svg-v2` is unavailable.

Keep this set to `true` during the migration period. It prevents older clients, unsupported browsers, or failed decoder initialization from losing audio entirely.

If fallback is disabled and the client cannot use `svg-v2`, the session may not have a compatible receive-audio path.

## Capability Negotiation

After joining, the web client sends a capability message that includes supported audio protocols and decoder support.

The server stores the selected transport mode for that websocket session:

| Client capability                      | Server result in `auto`         |
|----------------------------------------|---------------------------------|
| Supports `svg-v2` and Opus WASM decode | `svg-v2`                        |
| Missing capabilities                   | `legacy`                        |
| Unsupported decoder                    | `legacy` if fallback is enabled |
| Old client                             | `legacy` if fallback is enabled |

The browser path currently uses the WASM decoder path for `svg-v2`. WebCodecs fields may appear in capability metadata for future app/non-browser clients, but they are not the active browser decode path.

## Microphone capture

The web client requests the microphone through `getUserMedia` with browser processing turned **off**:

* `noiseSuppression: false`
* `echoCancellation: false`
* `autoGainControl: false`

Browser noise suppression, echo cancellation, and auto-gain often warp the mic signal when players use the web client over the internet (WAN). That can sound robotic or metallic to listeners. Disabling those constraints keeps a cleaner raw capture for remote voice.

This matches the intent of the stalled upstream change in PR #52, reapplied on current `dev`. Quiet mics can use the web **Mic Volume** / Input Sensitivity controls (software gain) instead of turning browser AGC back on.

## Multi-talk decode and mix

Opus is stateful per stream. Reusing one decoder for every speaker (or appending PCM for concurrent voices) can sound robotic when several people talk at once.

* Server legacy path: per-sender `OpusDecoder` in `SvgAudioListener`
* Client `svg-v2` path: per-sender Opus decoder pool (keyed by `FLAG_HAS_SENDER` UUID when present)
* Speaker worklet: per-voice queues mixed in `process()` with a soft clamp

## Developer Appendix: `svg-v2` Frame

`svg-v2` binary frames use little-endian multibyte fields:

| Field        |     Size | Description                   |
|--------------|---------:|-------------------------------|
| `magic`      |  2 bytes | ASCII `SV`                    |
| `version`    |   1 byte | `2`                           |
| `flags`      |   1 byte | Packet flags                  |
| `sequence`   |  4 bytes | Packet sequence number        |
| `panQ15`     |  2 bytes | Signed Q15 pan metadata       |
| `gainQ15`    |  2 bytes | Unsigned Q15 gain metadata    |
| `sampleRate` |  2 bytes | Sample rate, normally `48000` |
| `channels`   |   1 byte | Channel count                 |
| `codec`      |   1 byte | `1=opus`, `2=pcm16le`         |
| `payloadLen` |  4 bytes | Codec payload length          |
| `senderUUID` | 16 bytes | Optional; present when `FLAG_HAS_SENDER` (`0x08`) is set (big-endian UUID) |
| `payload`    | variable | Codec payload                 |

`FLAG_HAS_SENDER` (`0x08`) adds the speaker’s UUID after the 20-byte header so the web client can apply per-player receive volume. `payloadLen` still covers only the codec bytes.

See [Protocol reference](https://theodoremeyer.github.io/projects/simplevoicegeyser/protocol.md) for the full websocket message flow, join payload, capability packet, and audio packet formats.

Client mic Opus encoding is intentionally split into a future PR. PR #45 does not require client mic Opus encoding for `svg-v2` receive-audio transport to work.
