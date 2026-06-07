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

| Mode | Behavior |
| --- | --- |
| `legacy` | Server decodes incoming Simple Voice Chat Opus audio, applies distance/pan handling, converts to PCM, and sends PCM audio to the web client. |
| `svg-v2` | Server sends the original Opus payload plus compact spatial metadata. The web client decodes and applies spatial gain/pan locally. |
| `auto` | Server chooses `svg-v2` only when the web client reports compatible support. Otherwise it falls back to `legacy` when fallback is allowed. |

## Fallback Behavior

`server.audio.allow-legacy-fallback` controls whether the server can use `legacy` when `svg-v2` is unavailable.

Keep this set to `true` during the migration period. It prevents older clients, unsupported browsers, or failed decoder initialization from losing audio entirely.

If fallback is disabled and the client cannot use `svg-v2`, the session may not have a compatible receive-audio path.

## Capability Negotiation

After joining, the web client sends a capability message that includes supported audio protocols and decoder support.

The server stores the selected transport mode for that websocket session:

| Client capability | Server result in `auto` |
| --- | --- |
| Supports `svg-v2` and Opus WASM decode | `svg-v2` |
| Missing capabilities | `legacy` |
| Unsupported decoder | `legacy` if fallback is enabled |
| Old client | `legacy` if fallback is enabled |

The browser path currently uses the WASM decoder path for `svg-v2`. WebCodecs fields may appear in capability metadata for future app/non-browser clients, but they are not the active browser decode path.

## Developer Appendix: `svg-v2` Frame

`svg-v2` binary frames use little-endian multibyte fields:

| Field | Size | Description |
| --- | ---: | --- |
| `magic` | 2 bytes | ASCII `SV` |
| `version` | 1 byte | `2` |
| `flags` | 1 byte | Packet flags |
| `sequence` | 4 bytes | Packet sequence number |
| `panQ15` | 2 bytes | Signed Q15 pan metadata |
| `gainQ15` | 2 bytes | Unsigned Q15 gain metadata |
| `sampleRate` | 2 bytes | Sample rate, normally `48000` |
| `channels` | 1 byte | Channel count |
| `codec` | 1 byte | `1=opus`, `2=pcm16le` |
| `payloadLen` | 4 bytes | Payload length |
| `payload` | variable | Codec payload |

Client mic Opus encoding is intentionally split into a future PR. PR #45 does not require client mic Opus encoding for `svg-v2` receive-audio transport to work.
