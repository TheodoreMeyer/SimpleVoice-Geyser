---
title: Installation
layout: projects
project: simplevoicegeyser
---

# Installation

This guide covers installing and configuring Simple Voice Geyser on one server.

- Running several servers behind Velocity? Read [Velocity Proxy Setup]({% project_link velocity %}) after this page.
- For host-specific setup, see [Hosting Guides]({% project_link guides %}).

---

## Requirements

### Bukkit-based Servers (Paper / Purpur / Spigot)

* Minecraft **1.20.1 through 26.3** (full **26.x** line)
* Simple Voice Chat 2.6.x+ (use **2.6.24+** on Minecraft 26.3)
* GeyserMC 2.10.1-SNAPSHOT+ (for Bedrock players)
* Floodgate (optional, but recommended)

See [Compatibility]({% project_link install/compatibility %}) for the full version tables.

### Fabric

* Minecraft 26.1 or newer, with a matching Fabric Loader
* Fabric API (required)
* Simple Voice Chat 2.6.x+
* GeyserMC 2.10.1-SNAPSHOT+ (for Bedrock players)
* LuckPerms (optional)
* Floodgate (optional)

### Velocity Proxy Frontend (optional)

For networks running behind a Velocity proxy, a separate proxy plugin hosts the web client and moves voice sessions between backend servers automatically.

* Velocity 4.x and Java 25
* Simple Voice Geyser on each backend server

See: [Velocity Proxy Setup]({% project_link velocity %})

> Simple Voice Geyser depends on Simple Voice Chat and will not function without it.

---

## Installation Steps

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/simplevoice-geyser) or [GitHub](https://github.com/TheodoreMeyer/SimpleVoice-Geyser/releases).

2. Install the plugin/mod:

   * Bukkit: place the `.jar` in `plugins/`
   * Fabric: place the `.jar` in `mods/`

3. Install dependencies:

   * Simple Voice Chat
   * GeyserMC (if you have Bedrock players)
   * Optional: Floodgate, LuckPerms (Fabric)

4. Start or restart your server

5. Verify startup:

   * No errors in console
   * Plugin loads successfully
   * Voice Chat is functioning

---

## Open the Ports

Players need to reach two things on your server, and both must be allowed through your firewall and port forwarding:

| What | Protocol | Default port |
|------|----------|--------------|
| The voice page (this plugin) | TCP | `8080` (`server.port`) |
| Simple Voice Chat | UDP | `24454` (Simple Voice Chat's own `port`) |

The voice page port is **not** the Minecraft port. Simple Voice Chat sends the voice itself over **UDP**, so opening only TCP leaves players with the web page working but no voice. If the Simple Voice Chat mod shows the crossed-out speaker (plug) icon, the UDP port is closed.

---

## Share the Address With Your Players

Players need the address of the voice page from you. The plugin cannot know your public address, and the default join message does not contain it. Tell your players the address, or add it to `client.join-message.text` in the config below.

To serve the page on a normal HTTPS address or under a sub-path such as `https://example.com/svg/`, see [Proxy and Sub-path Deployment]({% project_link install/proxy-and-subpath %}) and [HTTPS and Reverse Proxies]({% project_link install/proxy %}).

---

## Server Configuration

The plugin is configured in `plugins/SimpleVoice-Geyser/config.yml` (Fabric: `config/SimpleVoice-Geyser/config.json` with the same keys, nested). Missing options are added automatically when the server starts, and a backup of your old file is kept.

```yaml
client:
  # Idle websocket timeout in minutes (0.5 to 10)
  idletimeout: 2

  # Only allow bedrock players to join the chat
  requireBedrock: false

  # Allows bedrock clients to open the SVG menu via emotes.
  # WARNING: Make sure off-hand-emote is disabled in Geyser to avoid conflicts.
  useEmoteForSVG: true

  # Allows players using web-chat to chat in-game
  web-chat-enabled: true

  join-message:
    # Enables join messages for web clients.
    enabled: true
    # The text svg sends when a player joins
    text:
      - "This Server Uses SimpleVoice-Geyser."
      - "To set it up, run /svg pswd [password],"
      - "Then join Via the server's SVG website."

  # Which clients may join. See "Who Can Join".
  allowedTypes:
    isBlackList: true
    list:

server:
  group:
    # The group web and app players are placed in.
    default:
      enabled: true
      password: 1a2b
      # Also move players who already have a group.
      force-on-web-join: false

  port: 8080
  # Use 127.0.0.1 to only allow host-local access.
  bind-address: 0.0.0.0
  # Where the voice page is served. /svg gives https://yourdomain.com/svg/
  context-path: /

  security:
    # Failed logins in auth-fail-duration minutes before a lockout.
    max-auth-failures: 5
    auth-fail-duration: 3
    # Lockout length in minutes.
    auth-lock-duration: 8

  audio:
    # auto | legacy | svg-v2
    transport-mode: auto
    allow-legacy-fallback: true
    mic:
      # Allow PCM microphone uploads for older clients.
      # Set false to require Opus-capable clients.
      allowLegacy: true

# Only used behind the Velocity proxy. See Velocity Proxy Setup.
proxy:
  enabled: false
  control-url: ""
  shared-secret: ""
  verify-ssl: true
  token-ttl-seconds: 120

# Debug logs. Do NOT use this in production.
debug: false

updatechecker:
  enable: true

# DO NOT CHANGE, or the config may be reset.
config-version: 0.1.5
```

The `config.yml` the plugin creates may show a few more comment lines. The file also contains `client.vctimeout`; it is not used by the current version.

### Important Notes

* **Join message:** shown to a player who has not set a password yet. On Paper, Purpur and Spigot the command is `/svg pswd <password> confirm`, so edit the text if you want players to see exactly that.
* **Default group:** players who join through the web page or the app are put in a group named `Svg` with the password `1a2b`. It is created automatically. Change the group's password, or turn it off, with `server.group.default`. See [Security]({% project_link install/security %}).
* **Who may join:** `client.allowedTypes` lets you allow only the web page, only the Svg-App, or both. See [Who Can Join]({% project_link joining/clients %}).
* **Idle timeout:** a player is disconnected after `client.idletimeout` minutes without voice traffic (2 by default, up to 10). On a Velocity network the proxy applies its own fixed limit of 2 minutes.
* **Microphone upload:** `server.audio.mic.allowLegacy: false` makes the server accept only Opus microphone audio. Clients that can only send the older PCM format, such as Svg-App 1.0.x, are refused. See [Audio Transport]({% project_link install/audio-transport %}).
* `useEmoteForSVG` may conflict with Geyser's off-hand emote setting.
* `bind-address` controls network exposure: `0.0.0.0` is accessible externally, `127.0.0.1` is local only.

---

## Networking & Security

Simple Voice Geyser serves its voice page over HTTP and WebSocket.

### Important:

* Browsers **require HTTPS** for microphone access in most environments
* Running on plain HTTP may result in:

   * microphone access being blocked
   * connection failures

### Recommendation

Use a reverse proxy (e.g., Nginx, Caddy) to provide:

* HTTPS (TLS)
* Secure WebSocket (WSS)

If your network runs behind a Velocity proxy, the SVG Velocity plugin can host the voice page and serve HTTPS itself. See: [Velocity Proxy Setup]({% project_link velocity %}).

---

## Upgrading

Some versions may introduce breaking changes.

See: [Upgrading]({% project_link install/upgrading %})

---

## Notes

* This plugin extends Simple Voice Chat; it does not replace it
* It does not provide voice chat on its own
* Players who run the Simple Voice Chat mod use the mod, not this page
* Versions that are not ready for production use may be marked as `-DEV`
