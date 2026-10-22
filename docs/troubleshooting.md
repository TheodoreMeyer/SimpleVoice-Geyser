---
title: TroubleShooting
layout: projects
project: simplevoicegeyser
---

# Trouble Shooting

## Unable to join Website
- Please look through ALL the docs before opening an issue.
- Previous Issues may be able to help.

## Server Not Binding
- Make sure that the Port the server is connected to (Default:8080) is open, and not taken by another server.

## Audio Sounds buggy
- Make sure you have a good internet access.
- If voice sounds **robotic or distorted** over a remote / WAN connection, see [Audio Transport → Microphone capture]({% project_link install/audio-transport %}).
- If voice only sounds bad when **several people talk at once**, see [Audio Transport → Multi-talk decode and mix]({% project_link install/audio-transport %}).

## Web chat panel only shows AudioRX / WebSocket lines
- Wipe `plugins/<SimpleVoice-Geyser>/web/` and hard-refresh so the client logs inbound `type: "chat"` packets.
- Ensure `client.web-chat-enabled: true` in `config.yml`.
- Only **public** Minecraft chat is mirrored (not whispers/commands). Cancelled chat (mute plugins) is skipped.

## People nearby hear my group (or I hear outsiders)
- A **password** only locks who may join. It does **not** mute nearby players.
- Audio isolation is the **group type** chosen when creating the group:
  - **Isolated** — only members hear each other (default on web create)
  - **Normal** — outsiders cannot hear you; you can still hear nearby non-members
  - **Open** — party line; nearby outsiders hear you too
- After create, the group menu title shows `(Isolated)` / `(Normal)` / `(Open)`. Wipe `plugins/.../web/` and hard-refresh if the type dropdown / hint is missing.

## Opening an issue:

* Ensure all dependencies match supported versions
* Check server logs for startup errors
* Verify Geyser and Simple Voice Chat are functioning independently

If issues persist, open an issue on the GitHub repository with:

* server type (Paper / Fabric, etc.)
* plugin versions
* logs 
    * client console
    * Server logs
    * client chatbox
    * Optional: debug logs on server
    * PLEASE NOTE: DO NOT share private information like usernames, passwords, and others in logs.