---
title: Velocity Proxy Setup
layout: projects
project: simplevoicegeyser
---

# Velocity Proxy Setup

If your network runs several servers behind a Velocity proxy, install the Simple Voice Geyser proxy plugin. Players then open one voice page, sign in once, and keep talking while they move between your servers. Without it, each server has its own voice page and a player would have to reconnect every time they change server.

This page walks through a full setup in the order that works: plan the ports, set up the backends, set up the proxy, open the firewall, then test. Take your time with the port plan. Most problems in a proxy setup are two services trying to use the same port.

## How it works

```
 Player's browser or app
          |
          |  one address, one login
          v
   Velocity proxy  (Svg-Velocity plugin)
     - serves the voice page
     - checks the player's password
     - follows the player from server to server
          |
          |  signed, short-lived pass for each backend
          v
 Backend servers  (each runs Simple Voice Geyser + Simple Voice Chat)
```

- Players only talk to the proxy. They never connect to a backend's voice page.
- The password is stored on the proxy. A player sets it by running `/svg pswd <password>` while connected through the proxy. On a proxy network do **not** add `confirm`; the proxy would treat it as part of the password.
- When the player changes server, the page shows "Changing server" and reconnects to the right backend by itself, without reloading and without asking for the password again.
- Each backend keeps its own voice groups. Moving servers does not carry a group over.
- Players load the voice page from the proxy. Custom page files placed on a backend do not apply to them.
- The proxy disconnects a connection with no voice traffic after 2 minutes. This limit is fixed.

## What goes where

- **Proxy:** `Svg-Velocity`, Geyser and Floodgate.
- **Each backend:** `Svg-Spigot` or `Svg-Fabric`, Simple Voice Chat and Floodgate.
- You need Velocity 4.x and Java 25 for the proxy plugin.
- **The proxy jar and the backend jars must be the same version.** The voice page that the proxy serves checks its version against the backend. A mismatch makes the page ask the player to update.
- Geyser and Floodgate go on the proxy for Bedrock players. The backends only need Floodgate. Geyser on the proxy is what lets Bedrock players use the password form.

## Step 1: Plan your ports

A proxy network has four separate services, and each one needs its own port:

| Service            | Protocol | Set in            |
|--------------------|----------|-------------------|
| Minecraft          | TCP      | `velocity.toml`   |
| Voice chat         | UDP      | Simple Voice Chat |
| Proxy voice page   | TCP      | `proxy.port`      |
| Backend voice page | TCP      | `server.port`     |

Rules that save you trouble:

1. **No two services on the same machine can share a port.** Simple Voice Geyser's default port is `8080` on the proxy and on every backend. Give each backend its own port, for example `8001` and `8002`.
2. **Players only need the proxy's voice page port.** Backend voice page ports only have to be reachable from the proxy.
3. **Each backend needs its own voice chat port** when they run on one machine. Every Simple Voice Chat server defaults to `24454`, so the second one cannot start. See "Simple Voice Chat ports" near the end of this page.

An example for one machine with two backends. Your numbers can differ.

| Service                   | Port    |
|---------------------------|---------|
| Proxy voice page          | `8080`  |
| Lobby voice page          | `8001`  |
| Survival voice page       | `8002`  |
| Lobby voice chat (UDP)    | `24454` |
| Survival voice chat (UDP) | `24455` |

Write your own list down before you continue. You will type each number twice, once where the service listens and once where something else connects to it.

## Step 2: Set up each backend

Install Simple Voice Geyser and Simple Voice Chat on the backend as usual, then edit Simple Voice Geyser's config. Spigot, Paper and Purpur use `plugins/SimpleVoice-Geyser/config.yml`. Fabric uses `config/SimpleVoice-Geyser/config.json` with the same keys, nested.

```yaml
server:
  port: 8001
  bind-address: 127.0.0.1

proxy:
  enabled: true
  control-url: "http://127.0.0.1:8080/api/player-state"
  shared-secret: "PASTE THE PROXY'S SECRET HERE"
```

- `server.port` is the backend's own voice page port from your plan. It must be different on every backend that shares a machine, and different from the proxy's.
- `server.bind-address` decides who can reach the backend's voice page. Use `127.0.0.1` when the proxy is on the same machine. When the proxy is on another machine, leave it at `0.0.0.0` and use the firewall to allow only the proxy's address.
- `proxy.enabled: true` tells the backend it sits behind the proxy.
- `proxy.control-url` is the proxy's address and port, with `/api/player-state` on the end. It is **not** the backend's own address. If players reach the proxy through Nginx or Caddy, the backend can still use the proxy's plain `http` address on the same machine or local network. If the plugin serves HTTPS itself (`ssl.type` is `file`), its port only speaks HTTPS, so use an `https` address here.
- `proxy.shared-secret` must be exactly the proxy's `proxy.shared_secret` (Step 3). Use the same value on every backend.
- Leave `server.context-path` at `/`. If you change it, the proxy needs to know. See Step 3.
- If the plugin serves HTTPS with a self-signed certificate, add `verify-ssl: false` under `proxy`.

You will restart the backends after Step 3, once you have the secret.

## Step 3: Set up the proxy

Put `Svg-Velocity-<version>.jar` in the proxy's `plugins` folder and start the proxy once. It creates `plugins/svg/config.json` and writes a random secret into it. Stop the proxy before you edit the file, because it only reads the file at start.

```json
{
  "clients": {
    "default":  { "enabled": true, "url": "ws://127.0.0.1:8001/ws" },
    "lobby":    { "enabled": true, "url": "ws://127.0.0.1:8001/ws" },
    "survival": { "enabled": true, "url": "ws://127.0.0.1:8002/ws" }
  },
  "proxy": {
    "bind_address": "0.0.0.0",
    "port": 8080,
    "transfer-timeout-seconds": 60,
    "shared_secret": "the random value created on first start",
    "token-ttl-seconds": 120
  },
  "ssl": {
    "type": "none",
    "file": { "cert": "ssl/cert.pem", "key": "ssl/key.pem" }
  }
}
```

### The `clients` section

This is the map from "which server is the player on" to "which backend voice page do I connect to".

- Each key is a server name exactly as it appears in your `velocity.toml`, capitals included.
- `url` is the backend's voice page address from Step 2, ending in `/ws`. Same machine: `127.0.0.1`. Another machine: that machine's address.
- If you changed a backend's `server.context-path` to `/svg`, the `url` must include it: `ws://127.0.0.1:8001/svg/ws`.
- `enabled: false` turns voice off for that server. A player standing on it sees "Proxy backend is not configured for this server."
- `default` is used for any server that has no entry. Set it to `enabled: false` if servers you did not list should have no voice.

### The `proxy` section

- `port` is the address players use. It must not clash with anything else on the machine.
- `bind_address`: `0.0.0.0` lets anyone reach it. Use `127.0.0.1` when Nginx or Caddy runs on the same machine and forwards to it.
- `shared_secret` is created for you. Copy it to every backend's `proxy.shared-secret`. Treat it like a password: anyone who has it can pretend to be any player. If it leaks, change it on the proxy and on every backend, then restart all of them.
- `transfer-timeout-seconds` is how long the proxy waits for a player to arrive on the next server before it drops their voice connection.
- `token-ttl-seconds` is how long the pass the proxy gives a backend stays valid.

Now start the proxy, then restart the backends.

## Step 4: HTTPS

Browsers only allow the microphone on HTTPS pages, so a public setup needs it. There are two ways.

**A reverse proxy in front (recommended).** Nginx or Caddy takes the public HTTPS address and forwards to the proxy plugin. Set `proxy.bind_address` to `127.0.0.1`. It must forward web socket upgrades as well as normal requests.

A complete Caddy site block. Caddy gets the certificate by itself:

```
voice.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

The same for Nginx, inside your HTTPS `server` block:

```
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
}
```

**HTTPS inside the plugin.** In `config.json` set `ssl.type` to `file`, then point `ssl.file.cert` and `ssl.file.key` at your files. They are read from the plugin's folder. The certificate is a PEM file. The key must be an unencrypted PKCS#8 PEM key. If you have a certificate chain or want automatic renewal, use the reverse proxy instead.

More in [HTTPS and reverse proxies]({% project_link install/proxy %}).

## Step 5: Open the firewall

| Port                | Protocol | Open to    |
|---------------------|----------|------------|
| Proxy voice page    | TCP      | Players    |
| Backend voice pages | TCP      | Proxy only |
| Simple Voice Chat   | UDP      | Players    |

The common mistake is opening only TCP. Simple Voice Chat sends voice over **UDP**. If you use Simple Voice Chat's proxy support, the voice traffic arrives on the proxy's Minecraft port, so allow UDP on that port too, for example `sudo ufw allow 25565/udp`. When UDP is blocked, Java players with the mod see the crossed-out speaker or "plug" icon even though everything else works.

## Step 6: Set passwords and test

1. Join the network with a Java or Bedrock account and run `/svg pswd <password>` (8 to 32 characters). It is stored on the proxy and works for every backend. Bedrock players can run `/svg` for a form.
2. Open the proxy's address in a browser or the Svg-App and sign in.
3. With two accounts on the same backend, check you can talk both ways.
4. Move one account to another server. The page says "Changing server", then reconnects. Check you can still talk.
5. Move to a server marked `enabled: false`. Voice should stop with a clear message.

Test with two accounts on the same backend first. Only then test moving between servers.

## Simple Voice Chat ports

If two backends run on the same machine, give each one a different `port` in Simple Voice Chat's `voicechat-server.properties`, for example `24454` and `24455`. If two use the same port, the second one cannot start its voice server. Java players then see the plug icon, and the voice page cannot join that backend after a transfer. The backend log shows a `BindException` for the voice chat port and "Unexpected authentication failure".

## Changing the setup later

- The proxy reads `config.json` only at start. Restart it after any change.
- `/svg reload` only reloads the backend's own config, even when you type it on the proxy, and it does not apply everything. Restart the backend after you change ports or the `proxy` section.
- Updating: replace the jar on the proxy and on every backend with the same version, then restart.

## When something goes wrong

- **"Update required" on the voice page:** the proxy and a backend jar are different versions.
- **"Proxy backend is not configured for this server":** there is no entry for that server and `default` is disabled, or its entry is `enabled: false`.
- **Login works, then the page closes at once:** the `url` is wrong, the backend is not running, or its `server.port` is different from the port in the `url`. Check the context path too.
- **"Proxy authentication is disabled or not configured" at login:** that backend has `proxy.enabled: false` or an empty `shared-secret`.
- **"Proxy authentication failed" at login:** the backend's `shared-secret` is different from the proxy's.
- **The proxy plugin does not start its page:** the proxy port is already in use, or `ssl.type` is `file` and the certificate or key file is missing.
- **A backend's voice page does not start:** its `server.port` is already in use by another backend or the proxy.
- **Plug icon in the Java client:** the UDP port is closed in the firewall, or two Simple Voice Chat servers share one port.
- **No password form for Bedrock players:** Geyser is not installed on the proxy.
- **Works at home, not from outside:** the proxy's voice page port is not forwarded, or the page is served without HTTPS.

## Who may join: web page, app, or both

That choice is made on each backend, not on the proxy. See [Who can join]({% project_link joining/clients %}).
