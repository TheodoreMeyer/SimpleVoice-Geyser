---
title: 0.1.2 Release Notes
layout: projects
project: simplevoicegeyser
---

# Simple Voice Geyser 0.1.2 Docs

- These pages supplement the existing website pages for installation, security, proxy setup, commands, and API notes. 
- They are intended to help server owners understand the behavior introduced around the 0.1.2 release cycle.
- They will be removed for future releases once the changes are well-known and the documentation is fully integrated into the main website.

## 0.1.2 release notes:
- Adds svg-v2 audio transport mode with client capability negotiation and legacy fallback.
    - This includes a new binary frame format that sends the original Opus payload and compact spatial metadata to the web client for local decoding and 3D spatialization.
- Adds config migration behavior to restore missing default keys without overwriting user-set values.
- Adds `/svg reload` command to reload config and run migration without restarting the server.
- Adds `server.context-path` config for root/subpath hosting and proxy compatibility.
- 

## Topics

- [Audio transport](https://theodoremeyer.github.io/projects/simplevoicegeyser/audio-transport.md): `legacy` vs `svg-v2`, capability negotiation, fallback behavior, and the binary frame layout.
- [Protocol reference](https://theodoremeyer.github.io/projects/simplevoicegeyser/protocol.md): websocket control messages, join payloads, capability packets, and audio packet formats.
- [Configuration and upgrades](https://theodoremeyer.github.io/projects/simplevoicegeyser/configuration-and-upgrades.md): config migration, backups, `/svg reload`, and currently inactive config values.
- [Proxy and subpath deployment](https://theodoremeyer.github.io/projects/simplevoicegeyser/proxy-and-subpath.md): `server.context-path`, root/subpath hosting, and websocket proxy requirements.
