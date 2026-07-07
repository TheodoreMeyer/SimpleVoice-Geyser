---
title: Installation
layout: projects
project: simplevoicegeyser
---

# Installation

This guide covers installing and configuring Simple Voice Geyser.

---

## Requirements

### Bukkit-based Servers (Paper / Purpur / Spigot)

* Minecraft 1.21.8+
* Simple Voice Chat 2.6.x+
* GeyserMC 2.9.0-SNAPSHOT+
* Floodgate (optional, but recommended)

### Fabric

* Fabric Loader (matching your Minecraft version)
* Fabric API (required)
* Simple Voice Chat 2.6.x+
* GeyserMC 2.9.0-SNAPSHOT+
* LuckPerms (optional)
* Floodgate (optional)

> Simple Voice Geyser depends on Simple Voice Chat and will not function without it.

---

## Installation Steps

1. Download the latest release:
   https://github.com/TheodoreMeyer/SimpleVoice-Geyser/releases

2. Install the plugin/mod:

   * Bukkit: place the `.jar` in `plugins/`
   * Fabric: place the `.jar` in `mods/`

3. Install dependencies:

   * Simple Voice Chat
   * GeyserMC
   * Optional: Floodgate, LuckPerms (Fabric)

4. Start or restart your server

5. Verify startup:

   * No errors in console
   * Plugin loads successfully
   * Voice Chat is functioning

---

## Server Configuration

The plugin is configured via:
```yaml
client:

   # default: 30
   # Timeout in seconds
   # WARNING: Developer Has disabled this for right now.
   vctimeout: 30

   # default: 2
   # idle websocket timeout in minutes
   idletimeout: 2

   # default: false
   # Only allow bedrock players to join the chat
   requireBedrock: false

   # default: true
   # Allows for the bedrock client to open a menu just by opening the emotes menu
   # WARNING: Make sure you have off-hand-emote turned off in geyser or there may
   #          be conflict
   useEmoteForSVG: true

  # default: true
  # Allows players using web-chat to chat in-game
   web-chat-enabled: true
   
updatechecker:
  enable: true

server:

   group:

      # Settings for the default group for Svg players.
      default:

         # default: true
         # This decides whether the Svg group is created for players who use the website
         enabled: true

         # default: 1a2b
         # This control's the default groups's password.
         password: 1a2b

   # default: 8080
   # port server will run on.
   port: 8080

   # default: 0.0.0.0
   # Address server binds to, use 127.0.0.1 to only connect from host device.
   # This is suggested to be changed only if you are running a proxy on the same network/device as the server to allow https/wss.
   bind-address: 0.0.0.0
   
   # default: /
   # This is the context-path, or where the root of the server will be generated.
   # Example: /svg will generate the server at https://yourdomain.com/svg/
   context-path: /
   
   security:
     
     # default: 5
     # This is the number of failed login attempts before the server will block the user for auth-lock-duration.
     max-auth-failures: 5
     
     # default: 3
     # How long each failure is saved for in minutes. 
     # Example: if set to 3, and max-auth-failures is 5, auth can fail 4 times in 3 minutes before being locked out.
     auth-fail-duration: 3

     # default: 5
     # This is the lockout in minutes when max-auth-failures has been reached.
     auth-lock-duration: 5
   
   # See more at https://theodoremeyer.github.io/projects/simplevoicegeyser/0.1.2/audio-transport/
   audio:
     
     # default: auto
     # options: legacy, svg-v2
     # This is the audio transport mode, deciding how to send audio to client.
     # legacy: uses the legacy audio transport mode, which is compatible with all clients.
     # svg-v2: uses the new svg-v2 audio transport mode, which is only compatible with clients that support it.
     # WARNING: This has been deprecated, and will be changed or removed in future releases.
     transport-mode: auto
     
     # default: true
     # Whether legacy mode is supported on your server.
     # If transport-mode is set to svg-v2, can legacy still be used.
     allow-legacy-fallback: true
     

# default: false
# Debug logs
# Do NOT use this in production. This will very quickly fill up your server logs.
Debug: false

# default: false
# Version of server associated with config
# DO NOT CHANGE, or config may be reset/messed up
config-version: 0.1.2
```

### Important Notes

* `vctimeout` is currently not enforced and may be removed or re-enabled later
* `useEmoteForSVG` may conflict with Geyser’s off-hand emote setting
* `bind-address` controls network exposure:

   * `0.0.0.0` → accessible externally
   * `127.0.0.1` → local only

---

## Networking & Security

Simple Voice Geyser uses a web-based client over HTTP/WebSocket.

### Important:

* Browsers **require HTTPS** for microphone access in most environments
* Running on plain HTTP may result in:

   * microphone access being blocked
   * connection failures

### Recommendation

Use a reverse proxy (e.g., Nginx, Caddy) to provide:

* HTTPS (TLS)
* Secure WebSocket (WSS)

---

## Upgrading

Some versions may introduce breaking changes.

See:
https://theodoremeyer.github.io/projects/simplevoicegeyser/upgrading/

---

## Versioning Notes

* Versions not be ready for production use may be marked as `-DEV`.

---

## Notes

* This plugin extends Simple Voice Chat; it does not replace it

* It does not provide voice chat on its own

* As of 0.1.2-DEV:

   * Audio Security
       * Client to server audio is not encoded
       * Server to client audio is only encoded in Svg-v2 audio protocol
   * The server runs over HTTP by default

This makes using HTTPS strongly recommended for real deployments.
