---
title: Upgrading
layout: projects
project: simplevoicegeyser
---

# Upgrading

This page documents version upgrade behavior and breaking changes.

---

## General Rules

- Upgrading to newer versions is supported unless stated otherwise
- Downgrading is **not supported**
- Always back up your server before upgrading
- Detailed per-release notes will live under `docs/releases/<version>.md` (see upcoming **0.2.0**)

---

## Version-Specific Notes

### Upgrade toward 0.2.0

- Svg-App version gate uses `client.require-latest` (default `true`: exact plugin version). Set `false` only as a temporary escape hatch to accept the same major.minor (any patch). Hardcoded `0.1.3` / `0.1.4` allowlists are removed.
- Folia support keeps Spigot API compile compatibility (EntityScheduler via reflection).
- `/svg reload` restarts Jetty; players need `svg.admin` (default op).
- Web **Create Group** chooses Isolated / Normal / Open with a live hint (default Isolated). Password is join-only; wipe `web/` after upgrade so the type UI loads.
- Minecraft public chat is mirrored into the web chat panel when `client.web-chat-enabled` is true (wipe `web/` so inbound `chat` packets are displayed).
- Voice Chat Settings restyled to match Simple Voice Chat Java (square sliders, full-width activation toggle, mic/speaker side by side). Wipe `web/` and hard-refresh (Ctrl+F5) after upgrade.
- Hub matches SVC Java icon row (mic | mute speaker/deafen | Adjust Volumes) with darker MC buttons. Wipe `web/` after upgrade.
- Adjust Volumes matches SVC (large avatar, wide amp bar behind name, no mute button — slide to 0%). Wipe `web/` after upgrade.
- Pending web join resolves Floodgate players already online even when `accounts.json` UUID is stale (name/dot fallback). Wipe `web/` after upgrade; re-run `/svg pswd` if stuck.
- Config backups move to `backups/configs/` (rotates to `config.backups.max-files`, default 6). Legacy root `config-*.yml.bak` files are migrated on the next backup.

---

### Upgrade to 0.0.3-DEV

- Password storage behavior changed
- Passwords are no longer persisted in plaintext
- Users must reset passwords after upgrading

#### Impact

- Existing users will lose access until passwords are reconfigured
- Improves overall security

### Upgrade to 0.1.1

- Password storage behavior changed
- Users must reset passwords after upgrading

#### Impact

- Existing users will lose access until passwords are reconfigured
- This removes redundancy and increases performance.

---

### Upgrade to 0.1.6 (web UI / reverse proxy)

If the join page shows empty labels/buttons (Username, Password, Join) behind Nginx Proxy Manager or another reverse proxy:

1. Stop the server
2. Delete the plugin web override folder: `plugins/<SimpleVoice-Geyser>/web/`
3. Replace the plugin JAR with `0.1.6` (or newer)
4. Start the server (full `/web/` tree is re-extracted, including `js/utils/lang.js`)
5. Hard-refresh the browser (`Ctrl+F5`)
6. Confirm NPM/proxy has **Websockets** enabled and is not caching `/js/` aggressively

---

### Upgrade to 0.1.7 (mic sensitivity / volume)

Adds **Input Sensitivity** and **Mic Volume** sliders in the connected web UI (Voice Activation + software gain; browser AGC stays off).

1. Replace the plugin JAR with `0.1.7`
2. If you use a web override folder, delete `plugins/<SimpleVoice-Geyser>/web/` so the new JS/CSS/HTML is extracted
3. Hard-refresh the browser (`Ctrl+F5`)
4. After join, use the sliders under Mute; preferences are stored in the browser (`localStorage`)

---

### Upgrade to 0.1.8 (join grace)

- **Join grace:** after a valid web password, SVG waits up to `client.vctimeout` (default 60s) for you to join Minecraft — useful when Bedrock kicks you for app-switching.

1. Replace the plugin JAR with `0.1.8`
2. Delete `plugins/<SimpleVoice-Geyser>/web/` if you use overrides; let new defaults merge into `config.yml`
3. Hard-refresh the browser (`Ctrl+F5`)
4. Optional: tune `client.vctimeout`

---

### Upgrade to 0.1.9 (web UI polish / NL / branding)

- Polished Blocky join layout (no clipped mic/speaker labels)
- **Dutch** language option (+ fuller JS strings for pl/it)
- Config branding: `client.web.default-language`, `client.web.server-name`, `client.web.tagline` (served at `/svg-web-config.json`)
- Error banners auto-dismiss after 60 seconds

1. Replace the plugin JAR with `0.1.9`
2. Delete `plugins/<SimpleVoice-Geyser>/web/` if you use overrides
3. Start server (new config keys merge) and hard-refresh (`Ctrl+F5`)
4. Set `client.web.*` as desired (e.g. `default-language: nl`)

---

### Upgrade to 0.1.10 (pending-join wait UI)

Clearer **Waiting for Minecraft** panel with countdown while join grace holds the WebSocket open.

1. Replace the plugin JAR with `0.1.10` (or newer)
2. Delete `plugins/<SimpleVoice-Geyser>/web/` if you use overrides
3. Hard-refresh the browser (`Ctrl+F5`)

---

### Upgrade to 0.1.11 (per-player Adjust Volumes)

Adds **Adjust Volumes** (Overall / Other / per-player amplification + mute) in the connected web UI. Requires the new jar (svg-v2 sender UUID + `voice_players` roster) and a refreshed web client.

1. Replace the plugin JAR with `0.1.11`
2. Delete `plugins/<SimpleVoice-Geyser>/web/` so new JS/CSS/HTML is extracted
3. Hard-refresh the browser (`Ctrl+F5`)
4. After join, use **Adjust Volumes** under the mic settings; prefs stay in browser `localStorage` (`svgReceiveVolumes_v1`)

Legacy PCM sessions remain master-volume only. Old clients against a new jar ignore unknown flags safely but will not get per-player control until updated.

---

### Upgrade to 0.1.12 (louder mic volume)

Fixes **Mic Volume** having little/no effect for listeners when browser AGC is off. Gain now uses a Web Audio `GainNode` (up to **600%**).

1. Replace the plugin JAR with `0.1.12`
2. Delete `plugins/<SimpleVoice-Geyser>/web/`
3. Hard-refresh (`Ctrl+F5`)
4. After join, raise **Mic Volume** until the energy bar reacts when you speak — others hear that louder signal

---

### Upgrade to 0.1.13 (proximity default + multi-talk audio)

- **Default group off:** new installs no longer auto-join the shared `Svg` OPEN group. For proximity, set in live `config.yml`:

```yaml
server:
  group:
    default:
      enabled: false
```

  (Existing configs that already have `enabled: true` keep that value until you change it.)

- **Multi-talk fix:** per-sender Opus decoders (server legacy + client svg-v2) and per-voice mixing in the speaker worklet so several people talking no longer sounds robotic.

1. Replace the plugin JAR with `0.1.13`
2. Set `server.group.default.enabled: false` if you still see everyone in group `Svg`
3. Delete `plugins/<SimpleVoice-Geyser>/web/`
4. Restart + hard-refresh (`Ctrl+F5`)

---

### Upgrade to 0.1.14 (Update required loop)

If the browser keeps showing **Update required. Reloading page.**:

1. Stop the server
2. Delete `plugins/<SimpleVoice-Geyser>/web/` (stale `index.html` BUILD_ID)
3. Install `0.1.14` (always refreshes `index.html` / `legacy.html` from the jar; client only auto-reloads once)
4. Start + hard-refresh (`Ctrl+F5`); clear NPM/browser cache if needed

---

### Upgrade to 0.1.15 (SVC-like multi-screen UI)

Connected web UI is now hub → Settings / Adjust Volumes / device lists (not one long page).

1. Stop the server
2. Delete `plugins/<SimpleVoice-Geyser>/web/` (required — new `index.html`, `screens.js`, CSS)
3. Install `0.1.15`
4. Start + hard-refresh (`Ctrl+F5`); clear NPM/browser cache if needed

---

### Upgrade to 0.1.16 (Web Groups list + join)

Web clients can list SVC groups and join with a password (hub **Groups** button). Adjust Volumes stays on the volumes icon.

1. Stop the server
2. Delete `plugins/<SimpleVoice-Geyser>/web/` (required — new Groups UI + protocol)
3. Install `0.1.16`
4. Start + hard-refresh (`Ctrl+F5`); clear NPM/browser cache if needed

Ensure players have `svg.vc.group.join` (included under `svg.vc.group` by default).

---

### Upgrade to 0.1.17 (UI polish + Create Group)

Slider labels, single Leave, master volume in Settings, and web Create Group.

1. Stop the server
2. Delete `plugins/<SimpleVoice-Geyser>/web/`
3. Install `0.1.17`
4. Start + hard-refresh (`Ctrl+F5`)

---

### Upgrade to 0.1.18 (In-group menu)

Clicking a group opens the member menu (SVC-style). Wipe web assets after upgrade.

1. Stop the server
2. Delete `plugins/<SimpleVoice-Geyser>/web/`
3. Install `0.1.18`
4. Start + hard-refresh (`Ctrl+F5`)

---

### Upgrade to 0.1.19 (Group password screen)

Password field is no longer on the groups list — only shown after clicking a locked group (SVC “Enter Group Password”).

1. Stop the server
2. Delete `plugins/<SimpleVoice-Geyser>/web/`
3. Install `0.1.19`
4. Start + hard-refresh (`Ctrl+F5`)

---

### Upgrade to 0.1.20 (Reload restarts web server)

`/svg reload` now restarts Jetty so port/bind/context-path/idle-timeout and related web settings apply without a full Minecraft restart. Web clients are disconnected.

1. Replace the plugin JAR with `0.1.20`
2. Restart once (or use `/svg reload` after install)
3. Optional: delete `web/` + hard-refresh if you also update the UI

---

### Upgrade to 0.1.21 (Clean address bar)

Temporary `?_svg=…` cache-bust query is removed from the URL after load (address bar stays clean).

1. Delete `plugins/<SimpleVoice-Geyser>/web/`
2. Install `0.1.21`
3. Restart + hard-refresh (`Ctrl+F5`)

---

### Groups create navigation + type (toward 0.2.0)

Creating a group from the web UI now opens the in-group menu (fixes a race where a premature `groups` push cleared pending navigation). Create offers **Isolated / Normal / Open** with a live hint (default **Isolated**). Password is join-only; it does not make audio private — pick **Isolated** (or **Normal**) for that.

1. Delete `plugins/<SimpleVoice-Geyser>/web/`
2. Install the new jar
3. Restart + hard-refresh (`Ctrl+F5`)
4. Confirm the create screen shows a type hint under the dropdown; after create the group menu title includes `(Isolated)` / `(Normal)` / `(Open)`

---

## Best Practices

- Test upgrades in a staging environment
- Read changelogs before updating
- Avoid upgrading production servers blindly

---

## DEV Builds

- `-DEV` versions are experimental
- Expect breaking changes
- Not recommended for production use