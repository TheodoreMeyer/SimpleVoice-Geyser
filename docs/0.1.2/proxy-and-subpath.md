---
title: Proxy and Sub-path Deployment
layout: projects
project: simplevoicegeyser
---

# Proxy And Subpath Deployment

- Simple Voice Geyser can be served from the web server root or from a configured subpath.
- This shows project config got setting up proxy and sub-path stuff.
    - Please note this does not include nginx or proxy config.

## Context Path

The relevant config key is:

```yaml
server:
  context-path: /
```

Use `/` when the web UI is served at the root of the host, for example:

```text
https://voice.example.com/
```

Use a subpath when the web UI is mounted under another site path, for example:

```yaml
server:
  context-path: /svg
```

```text
https://example.com/svg/
```

## Reverse Proxy Requirements

The reverse proxy must forward both normal HTTP requests and websocket upgrade requests to the Simple Voice Geyser web server.

Required behavior:

| Requirement                          | Reason                                                                                              |
|--------------------------------------|-----------------------------------------------------------------------------------------------------|
| Preserve the configured path/subpath | Static assets and websocket routes are resolved relative to the web UI path.                        |
| Forward websocket upgrades           | The voice web client connects over WebSocket.                                                       |
| Use HTTPS/WSS for public deployments | Browsers restrict microphone access outside secure contexts, and HTTPS protects traffic in transit. |

## Root vs Subpath

Root deployments are the simplest because all assets and the websocket endpoint live under `/`.

Subpath deployments require the proxy path and `server.context-path` to match. If the proxy serves `/svg/` but the plugin is configured for `/`, static assets or websocket routing may fail.

## Admin Checklist

- Confirm `server.context-path` matches the path exposed by the proxy.
- Confirm websocket upgrade headers are forwarded.
- Prefer `https://` and `wss://` for real deployments.
- Restart the server after changing web server binding or path settings if the running server does not pick up the change.
