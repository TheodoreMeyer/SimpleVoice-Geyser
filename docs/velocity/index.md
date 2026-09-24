---
title: Velocity Proxy Setup
layout: projects
project: simplevoicegeyser
---

# Velocity Proxy Setup

Simple Voice Geyser ships an optional **Velocity plugin** that acts as a web frontend for networks running multiple backend servers. Instead of every backend hosting its own web server, browsers connect to Velocity, and Velocity keeps each player's voice session alive while they move between servers.

This page covers installing and configuring the Velocity module. For HTTPS termination with Nginx/Caddy in front of it, see [HTTPS / Reverse Proxy Setup]({% project_link install/proxy %}).

---

## How It Works

```
Browser ──wss──> Velocity web frontend ──ws──> Backend SVG (Simple Voice Chat)
                    │
                    └─ tracks sessions per player and re-routes them
                       when the player switches Velocity servers
```

* The proxy hosts an embedded Jetty web server serving:
  * The static web client
  * `/ws` — the WebSocket endpoint browsers connect to
  * `/api/player-state` — a control endpoint backends call to report join/leave events
* When a player switches servers on the network, their browser session is transparently moved to the backend matching the new server — no reconnect or password prompt.
* The proxy signs short-lived auth tokens for each browser session. Backends validate these tokens using a shared secret, so players joining through the proxy do not need to re-enter passwords per backend.

---

## Requirements

* Velocity proxy (3.x)
* Simple Voice Geyser installed on **each backend server** (Paper/Purpur/Spigot or Fabric) with Simple Voice Chat
* Java 21+

> The Velocity plugin does not replace the backend plugin/mod — backends still run SVG and Simple Voice Chat as normal.

---

## Installation

1. Download the Velocity jar from the
   https://github.com/TheodoreMeyer/SimpleVoice-Geyser/releases
2. Place it in your Velocity proxy's `plugins/` directory
3. Restart the proxy. A default config is generated at
   `plugins/simplevoice-geyser/config.json`
4. On first start a random `proxy.shared_secret` is generated automatically
5. Configure the backend entries (`clients`) and backend servers as described below

---

## Proxy Configuration

The proxy is configured via `plugins/simplevoice-geyser/config.json`:

```json
{
  "clients": {
    "default": {
      "enabled": true,
      "url": "ws://127.0.0.1:8001/ws",
      "verify_ssl": false,
      "auth": { "global": true }
    },
    "lobby": {
      "enabled": false,
      "url": "ws://127.0.0.1:8002/ws",
      "verify_ssl": false,
      "auth": { "global": false, "secret": "" }
    }
  },
  "proxy": {
    "bind_address": "0.0.0.0",
    "port": 8080,
    "transfer-timeout-seconds": 60,
    "shared_secret": "GENERATED_ON_FIRST_START",
    "token-ttl-seconds": 120
  },
  "ssl": {
    "type": "none",
    "file": {
      "cert": "ssl/cert.pem",
      "key": "ssl/key.pem"
    }
  },
  "config_version": "0.1.4"
}
```

### `clients`

Each entry under `clients` maps a **Velocity server name** (as defined in `velocity.toml`) to the SVG websocket of the backend running there.

| Key                     | Default                  | Description                                                                 |
|-------------------------|--------------------------|-----------------------------------------------------------------------------|
| `enabled`               | `true`                   | Whether this backend can be connected to                                    |
| `url`                   | `ws://127.0.0.1:8001/ws` | Backend SVG websocket URL (must include the `/ws` path)                      |
| `verify_ssl`            | `false`                  | Reserved for TLS-protected backend URLs; not enforced by current builds      |
| `auth.global`           | `true`                   | Use the global `proxy.shared_secret` to sign tokens for this backend         |
| `auth.secret`           | `""`                     | Per-backend signing secret, used when `auth.global` is `false`. Required if set |

A special `default` entry is used when a player's current server has no matching entry. Unknown or blank server names fall back to `default`.

### `proxy`

| Key                        | Default      | Description                                                            |
|----------------------------|--------------|------------------------------------------------------------------------|
| `bind_address`             | `0.0.0.0`    | Address the web frontend binds to. Use `127.0.0.1` behind a local reverse proxy |
| `port`                     | `8080`       | Port the web frontend listens on                                       |
| `transfer-timeout-seconds` | `60`         | How long a session may stay in transfer before being dropped           |
| `shared_secret`            | *(random)*   | Secret shared with backends. Auto-generated on first start             |
| `token-ttl-seconds`        | `120`        | Lifetime of signed browser auth tokens                                 |

> Keep `shared_secret` private. Anyone holding it can forge valid browser identities against your backends.

---

## Backend Configuration

Each backend server running Simple Voice Geyser must be told it lives behind the proxy:

```yaml
proxy:
  # Set true when this server is running behind the SVG Velocity proxy.
  enabled: true

  # URL of Velocity's player-state endpoint.
  control-url: "http://127.0.0.1:8080/api/player-state"

  # Must match Velocity's proxy.shared_secret.
  shared-secret: "<same value as on the proxy>"

  # Verify TLS certificates when control-url uses https.
  verify-ssl: true

  token-ttl-seconds: 120
```

Notes:

* `control-url` must point at the Velocity web frontend (the same host/port browsers use), **not** at the backend itself
* `shared-secret` must exactly match the proxy's `proxy.shared_secret`
* If Velocity terminates TLS with a self-signed certificate, set `verify-ssl: false`
* Keep the backend's own `server.port`/web UI disabled or firewalled when running behind the proxy — players should connect through Velocity only

---

## Built-in TLS

The proxy can terminate TLS directly instead of relying on a reverse proxy:

```json
"ssl": {
  "type": "file",
  "file": {
    "cert": "ssl/cert.pem",
    "key": "ssl/key.pem"
  }
}
```

* Set `ssl.type` to `file` to enable HTTPS/WSS on the frontend listener
* `ssl.file.cert` must be an X.509 PEM certificate
* `ssl.file.key` must be an **unencrypted PKCS#8 PEM private key** matching the certificate
* Paths are resolved relative to the plugin configuration directory (`plugins/simplevoice-geyser/`)
* A reverse proxy remains recommended when you have PEM chains (fullchain) or want automatic renewal via Let's Encrypt — see [HTTPS / Reverse Proxy Setup]({% project_link install/proxy %})

---

## Commands

The proxy registers `/svg`:

| Command                | Description                                        |
|------------------------|----------------------------------------------------|
| `/svg pswd <password>` | Set (or change) the password for the web client     |
| `/svg help`            | Show usage help                                     |

Console can only see usage help; passwords must be set by players. Passwords are stored per player on the proxy and hashed.

---

## Security Notes

* Always serve the frontend over HTTPS/WSS in production — browsers block microphone access outside secure contexts
* Protect `proxy.shared_secret`; rotate it if exposed
* Tokens are short-lived (`token-ttl-seconds`) and bound to the player's UUID and username
* The `/api/player-state` endpoint rejects requests without a valid `X-SVG-Proxy-Secret` header
* Only one browser session is allowed per player; a new login replaces the previous one
