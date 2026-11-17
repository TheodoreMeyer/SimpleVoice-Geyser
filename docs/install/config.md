---
title: Configuration
layout: projects
project: simplevoicegeyser
---

# Configuration

Every setting for a server lives in one file. Find what you want to change below. The dropdowns walk you through the common changes step by step.

| Server type              | File                                    |
|--------------------------|-----------------------------------------|
| Paper, Purpur and Spigot | `plugins/SimpleVoice-Geyser/config.yml` |
| Fabric                   | `config/SimpleVoice-Geyser/config.json` |

Fabric uses the same names, written as nested JSON. `client.idletimeout` becomes `"client": { "idletimeout": 2 }`.

Options that are missing are added when the server starts, and a copy of your old file is kept.

After you change a file, run `/svg reload` (permission `svg.admin`, or the console). Reload does not apply everything. A change to a port, the bind address, the path or the `proxy` section needs a restart.

---

## Players and chat

| Option                    | Default   | What it does                                  |
|---------------------------|-----------|-----------------------------------------------|
| `client.idletimeout`      | `2`       | Minutes of silence before a player is dropped |
| `client.requireBedrock`   | `false`   | Only Bedrock players may join                 |
| `client.useEmoteForSVG`   | `true`    | Bedrock players open the menu with an emote   |
| `client.web-chat-enabled` | `true`    | Web and app players can write in game chat    |
| `client.join-message`     | on        | Message for players who have no password yet  |
| `client.allowedTypes`     | allow all | Which clients may join: the page, the app     |

The idle timeout can be 0.5 to 10 minutes. On a Velocity network the proxy uses its own fixed 2 minutes.

If `useEmoteForSVG` clashes with Geyser, turn off `off-hand-emote` in Geyser's config.

{% dropdownStart Allow only the web page, or only the app %}

By default both are allowed. The setting has two parts:

- `list` holds client names. There are two: `Web` (the browser page) and `Svg-App` (the Android app).
- `isBlackList: true` blocks the names in the list. `isBlackList: false` allows only the names in the list.

**I want only the web page (block the app)**

1. Open the config file.
2. Set:

   ```yaml
   client:
     allowedTypes:
       isBlackList: true
       list:
         - Svg-App
   ```

3. Run `/svg reload`.
4. Check it: sign in with the app. It says "This server does not allow the Svg-App client."

**I want only the app (block the web page)**

1. Open the config file.
2. Set:

   ```yaml
   client:
     allowedTypes:
       isBlackList: true
       list:
         - Web
   ```

3. Run `/svg reload`.
4. Check it: sign in from the browser. It says "Unsupported client type."

**I want everyone again**

Set `list` back to empty: `list: []`, then run `/svg reload`. To block both clients, list both names.

On Fabric the same rule looks like this:

```json
{
  "client": {
    "allowedTypes": {
      "isBlackList": true,
      "list": ["Svg-App"]
    }
  }
}
```

Good to know:

- Players who are already connected stay connected until they leave. The new rule applies the next time somebody signs in.
- This blocks the sign-in. It does not hide the page. To make the page unreachable, do not open its port.
- Java players who use the Simple Voice Chat mod are not affected.
- Joining voice also needs the permission `svg.vc.join`, which everyone has by default.
- On a Velocity network, set this on every backend. The proxy does not apply it.

{% dropdownEnd %}

{% dropdownStart Tell players where the voice page is %}

The plugin cannot know your public address, so the default join message does not contain it. Players see the message when they join the game without a voice password.

1. Open the config file.
2. Edit `client.join-message.text`. Add your address, and on Paper, Purpur and Spigot end the password line with `confirm`:

   ```yaml
   client:
     join-message:
       enabled: true
       text:
         - "Voice chat: https://voice.example.com"
         - "Set your voice password with /svg pswd <password> confirm"
   ```

3. Run `/svg reload`.

{% dropdownEnd %}

---

## The voice page

| Option                | Default   | What it does                               |
|-----------------------|-----------|--------------------------------------------|
| `server.port`         | `8080`    | TCP port of the voice page                 |
| `server.bind-address` | `0.0.0.0` | `127.0.0.1` keeps the page on this machine |
| `server.context-path` | `/`       | Path the page is served from               |

The page port is not the Minecraft port. Players also need Simple Voice Chat's own UDP port. See [Installation]({% project_link install %}).

To open the page at an address like `https://example.com/svg/`, see [HTTPS and Reverse Proxies]({% project_link install/proxy %}). Restart the server after you change any of these three options.

---

## The default group

The options are under `server.group.default`.

| Option              | Default | What it does                          |
|---------------------|---------|---------------------------------------|
| `enabled`           | `true`  | Put page and app players in one group |
| `password`          | `1a2b`  | The group's password                  |
| `force-on-web-join` | `false` | Also move players already in a group  |

{% dropdownStart Change the default group password %}

Page and app players are put in a group called `Svg`. The password `1a2b` is public, because it is in these docs, so change it.

1. Open the config file.
2. Set `server.group.default.password` to your own password. To turn the group off, set `server.group.default.enabled` to `false`.
3. Run `/svg reload`.

The new password applies the next time the group is created. A group that already exists keeps its old password until everyone has left it.

{% dropdownEnd %}

---

## Login protection

The options are under `server.security`.

| Option               | Default | What it does                      |
|----------------------|---------|-----------------------------------|
| `max-auth-failures`  | `5`     | Wrong logins before a lockout     |
| `auth-fail-duration` | `3`     | Minutes in which they are counted |
| `auth-lock-duration` | `8`     | Minutes a lockout lasts           |

With the defaults, 5 wrong logins within 3 minutes lock the player out for 8 minutes. See [Security]({% project_link install/security %}).

---

## Audio

You only need these if voice sounds wrong. The defaults work for most servers.

The options are under `server.audio`.

| Option                  | Default | What it does                                    |
|-------------------------|---------|-------------------------------------------------|
| `transport-mode`        | `auto`  | How sound reaches clients: auto, legacy, svg-v2 |
| `allow-legacy-fallback` | `true`  | Let older clients use the legacy path           |
| `mic.allowLegacy`       | `true`  | Accept the older microphone format              |

{% dropdownStart Require the newer microphone format %}

Modern clients send the microphone as {% hoverStart **Opus** %}

A compressed audio format that uses far less bandwidth.

{% hoverEnd %}. Older clients, such as Svg-App 1.0.x, send {% hoverStart **PCM** %}

Raw uncompressed audio.

{% hoverEnd %}. Both are accepted by default.

1. Open the config file.
2. Set `server.audio.mic.allowLegacy` to `false`.
3. Run `/svg reload`.
4. Check it: a client that can only send PCM is refused with "This server requires Opus microphone audio."

{% dropdownEnd %}

{% dropdownStart What the sound transport modes mean %}

| Mode     | What the server does                                               |
|----------|--------------------------------------------------------------------|
| `auto`   | Uses `svg-v2` for clients that support it, `legacy` for the others |
| `svg-v2` | Same as `auto` today                                               |
| `legacy` | Always converts the sound on the server and sends it as raw audio  |

Keep `allow-legacy-fallback` on `true`. If you turn it off, a client that cannot use `svg-v2` may get no sound at all.

Developers can read how the sound is packed in [Audio Transport]({% project_link api/audio-transport %}).

{% dropdownEnd %}

---

## Velocity network

The `proxy` section of a backend's config only matters behind a Velocity proxy. It is `proxy.enabled`, `proxy.control-url`, `proxy.shared-secret` and `proxy.verify-ssl`. Fill it in by following [Velocity Proxy Setup]({% project_link velocity %}).

---

## Everything else

| Option                 | Default | What it does                         |
|------------------------|---------|--------------------------------------|
| `debug`                | `false` | Extra log lines. Not for production  |
| `updatechecker.enable` | `true`  | Check for new versions               |
| `config-version`       | current | Do not change. The file may be reset |

The file also contains `client.vctimeout`. The current version does not use it.

{% dropdownStart The full default file %}

```yaml
client:
  idletimeout: 2
  requireBedrock: false
  useEmoteForSVG: true
  web-chat-enabled: true

  join-message:
    enabled: true
    text:
      - "This Server Uses SimpleVoice-Geyser."
      - "To set it up, run /svg pswd [password],"
      - "Then join Via the server's SVG website."

  allowedTypes:
    isBlackList: true
    list:

server:
  group:
    default:
      enabled: true
      password: 1a2b
      force-on-web-join: false

  port: 8080
  bind-address: 0.0.0.0
  context-path: /

  security:
    max-auth-failures: 5
    auth-fail-duration: 3
    auth-lock-duration: 8

  audio:
    transport-mode: auto
    allow-legacy-fallback: true
    mic:
      allowLegacy: true

proxy:
  enabled: false
  control-url: ""
  shared-secret: ""
  verify-ssl: true
  token-ttl-seconds: 120

debug: false

updatechecker:
  enable: true

config-version: 0.1.5
```

{% dropdownEnd %}
