---
title: Compatibility
layout: projects
project: simplevoicegeyser
---

# Compatibility

This page outlines supported environments, tested versions, and known limitations.

---

## Current Release (0.1.5)

- **Paper, Purpur, Spigot:** Minecraft 1.20.1 through 26.3
- **Fabric:** Minecraft 26.1 and newer, with Fabric API
- **Velocity proxy plugin:** Velocity 4.x and Java 25
- **Simple Voice Chat:** 2.6.x or newer (2.6.24 or newer on Minecraft 26.3)
- **Geyser:** 2.10.1-SNAPSHOT or newer, **Floodgate:** 2.2.5-SNAPSHOT or newer
- **Browsers:** Chrome and Firefox
- **Svg-App:** 1.1.0 (Android 7.0 or newer)

The tables below list earlier versions too and can be scrolled sideways.

---

## Browser Compatibility

Simple Voice Geyser uses a web-based client that relies on WebRTC. Browser support may vary depending on implementation differences.

| Version     | Chrome | Firefox | Edge |
|-------------|--------|---------|------|
| 0.0.1-DEV   | ✅      | ❌       | ❌    |
| 0.0.2-DEV   | ✅      | 🛠️     | ❌    |
| 0.0.3-DEV   | ✅      | 🚧      | ❌    |
| 0.1.0-DEV   | ✅      | ✅       | 🚧   |
| 0.1.1-0.1.5 | ✅      | ✅       | 🚧   |

---

## Bukkit-based Servers (Paper / Purpur / Spigot)

| Version   | MC Version | Voice Chat | Geyser           | Floodgate       | Status |
|-----------|------------|------------|------------------|-----------------|--------|
| 0.0.1-DEV | 1.21.8     | 2.5.27     | NONE             | NONE            | ❌      |
| 0.0.2-DEV | 1.21.8+    | 2.6.x      | NONE             | NONE            | ❌      |
| 0.0.3-DEV | 1.21.8+    | 2.6.x      | 2.9.0-SNAPSHOT   | 2.2.4-SNAPSHOT+ | ❌      |
| 0.1.0-DEV | 1.20.1+    | 2.6.x      | 2.9.0-SNAPSHOT+  | 2.2.4-SNAPSHOT+ | ❌      |
| 0.1.1     | 1.20.1+    | 2.6.x      | 2.9.0-SNAPSHOT+  | 2.2.4-SNAPSHOT+ | ❌     |
| 0.1.2     | 1.20.1+    | 2.6.x      | 2.10.0-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | ⚠️      |
| 0.1.3     | 1.20.1–26.3 | 2.6.x      | 2.10.1-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | ✅      |
| 0.1.4     | 1.20.1–26.3 | 2.6.x      | 2.10.1-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | ✅      |
| 0.1.5     | 1.20.1–26.3 | 2.6.x      | 2.10.1-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | ✅      |

### Requirements

* Minecraft **1.20.1 through 26.3** (full **26.x** line for Paper / Purpur / Spigot)
* Simple Voice Chat 2.6.x+ (use **2.6.24+** on Minecraft 26.3)
* GeyserMC 2.10.1-SNAPSHOT+ (Optional)
* Floodgate 2.2.5-SNAPSHOT+ (optional)

---

## Fabric

| Version   | MC Version | Voice Chat | Geyser           | Floodgate       | LuckPerms             | Status |
|-----------|------------|------------|------------------|-----------------|-----------------------|--------|
| 0.1.0-DEV | 1.21.11+   | 2.6.x      | 2.9.0-SNAPSHOT+  | 2.2.4-SNAPSHOT+ | compatible w/ 5.4 api | ❌      |
| 0.1.1-DEV | 1.21.11+   | 2.6.x      | 2.9.0-SNAPSHOT+  | 2.2.4-SNAPSHOT+ | compatible w/ 5.4 api | ❌      |
| 0.1.2-DEV | 26.1+      | 2.6.x      | 2.10.0-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | compatible w/ 5.4 api | ⚠️      |
| 0.1.3-DEV | 26.1+      | 2.6.x      | 2.10.1-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | compatible w/ 5.5 api | ✅      |
| 0.1.4     | 26.1+      | 2.6.x      | 2.10.1-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | compatible w/ 5.5 api | ✅      |
| 0.1.5     | 26.1+      | 2.6.x      | 2.10.1-SNAPSHOT+ | 2.2.5-SNAPSHOT+ | compatible w/ 5.5 api | ✅      |

### Requirements

* Fabric Loader (matching your Minecraft version)
* Fabric API (required)
* Simple Voice Chat 2.6.x+

### Optional Integrations

* Floodgate (for Bedrock account linking)
* LuckPerms (for permissions handling)
* GeyserMC 2.10.1-SNAPSHOT+ (for Bedrock compatibility)

---

## Velocity Proxy

| Version | Velocity | Java | Status |
|---------|----------|------|--------|
| 0.1.5   | 4.x      | 25+  | ✅      |

The proxy plugin and every backend must run the same Simple Voice Geyser version. See [Velocity Proxy Setup]({% project_link velocity %}).

---

## Svg-App (Android)

| App version | Android | Server versions |
|-------------|---------|-----------------|
| 1.1.0       | 7.0+    | 0.1.3, 0.1.4, 0.1.5 |

See [Svg-App]({% project_link app %}).

---

## Status Key

* ✅ Fully supported and tested
* ⚠️ Partially supported (may contain bugs or missing features)
* 🚧 Experimental (not production-ready)
* 🛠️ In development (actively being worked on)
* ❌ Unsupported / broken

---

## Known Limitations

* Chrome is currently the only fully supported browser
* Firefox support is incomplete due to WebRTC permission and audio handling differences
* Edge is untested and should be considered unsupported

---

## Recommendation

For the best experience, use:

* Latest version of the Browser (preferably Chrome).
* Matching versions from the tables above
* Fully updated dependencies per platform

Avoid mixing incompatible or outdated components, as this may result in connection failures or degraded audio quality.
