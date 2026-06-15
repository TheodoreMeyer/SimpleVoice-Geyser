# Simple Voice Geyser 0.1.2 Docs

These pages supplement the existing website pages for installation, security, proxy setup, commands, and API notes. They are intended to help server owners understand the behavior introduced around the 0.1.2 release cycle.

## Topics

- [Audio transport](https://theodoremeyer.github.io/projects/simplevoicegeyser/audio-transport.md): `legacy` vs `svg-v2`, capability negotiation, fallback behavior, and the binary frame layout.
- [Protocol reference](https://theodoremeyer.github.io/projects/simplevoicegeyser/protocol.md): websocket control messages, join payloads, capability packets, and audio packet formats.
- [Configuration and upgrades](https://theodoremeyer.github.io/projects/simplevoicegeyser/configuration-and-upgrades.md): config migration, backups, `/svg reload`, and currently inactive config values.
- [Proxy and subpath deployment](https://theodoremeyer.github.io/projects/simplevoicegeyser/proxy-and-subpath.md): `server.context-path`, root/subpath hosting, and websocket proxy requirements.
