# Live audio verification handoff (Browser → SVC → Java)

Unit and browser tests cannot certify that a Java Simple Voice Chat listener hears clean speech. Use this checklist on a live Paper/Folia + SVC server with the packaged Spigot JAR.

## What automated tests already prove

| Claim | Evidence |
| --- | --- |
| PCM frames are 960 samples / 1920 bytes | Java + JS unit tests |
| Resampler preserves pitch across 8–96 kHz | `resampler.test.mjs`, `OutboundMicContractTest` |
| Monotonic 20 ms pacing without shared-pool `Thread.sleep` | `SessionAudioPacingTest` |
| Idle stream ends with one `AudioSender.reset()` | `SessionAudioPacingTest` |
| Group gate blocks audio until confirmed join | `VoicePrivacyGateTest` |
| Audio limiter tolerates a 10-frame browser wake-up burst | `RateLimitServiceTest` |
| Client VAD hangover is 400 ms | `microphone.js` |

## Still requires a Java SVC listener

- Robotic / chopped speech perception
- Whether `AudioSender.send()` returns false under load
- End-to-end Opus packet loss after encode

## DEBUG matrix (run in order)

Set `debug: true` in `config.yml`. In the browser console after READY + group join:

```js
localStorage.setItem("svg.debug.tone", "1");
// Optional: SvgAudio.setVadBypass(true)
window.svgAudio?.startDiagnosticTone?.(10, 1000, 0.2);
```

| Mode | Config / client | What to listen for |
| --- | ---: | --- |
| Baseline | `rate-limits.audio.bypass: true`, VAD bypass on, processing off | Must be clean before enabling other stages |
| VAD only | bypass limiter, VAD on | Chopped syllables → VAD |
| Limiter only | bypass false, VAD off | Alternating drop/accept → limiter |
| Processing only | EC/NS/AGC on | Distortion → capture constraints |
| Production | defaults | Combined |

Server log lines to collect (no PCM):

- `AudioPipeline[...]` summaries (`sendIntervalMs`, `missedDeadline`, `streamReset`, `sendReject`, `qDrop`)
- `WebSocket: Dropping rate-limited binary frame`
- Client `[Audio] diag` / `[Audio] stage=ws-send` (sequence, 1920 bytes, ~50 fps)

## Pass criteria for live sign-off

1. Exactly one active browser pipeline (`activePipelines=1`).
2. Browser emits ~50 fps of 1920-byte frames while the tone runs.
3. Server mean send interval ≈ 20 ms (not bursty &lt; 5 ms, not stuck &gt; 40 ms).
4. No rapid `streamReset` during continuous tone/speech.
5. With limiter bypass vs on: if only “on” sounds robotic, repair the limiter — do not change resampling.
6. With VAD bypass vs on: if only “on” is chopped, repair VAD hangover/reset — do not change pacing.
7. Java listener hears a steady 1 kHz tone for ~10 s without robotic artifacts.
