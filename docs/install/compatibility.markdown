---
title: Compatibility
layout: projects
project: simplevoicegeyser
---

# Compatibility

This page outlines supported environments, tested versions, and known limitations.

---

## Browser Compatibility

Simple Voice Geyser uses a web-based client that relies on WebRTC. Browser support may vary depending on implementation differences.

| Version   | Chrome | Firefox | Edge |
|-----------|--------|---------|------|
| 0.0.1-DEV | ✅      | ❌       | ❌    |
| 0.0.2-DEV | ✅      | 🛠️     | ❌    |
| 0.0.3-DEV | ✅      | 🚧      | ❌    |
| 0.1.0-DEV | ✅      | ✅       | 🚧   |
| 0.1.1     | ✅      | ✅       | 🚧   |

---

## Bukkit-based Servers (Paper / Purpur / Spigot)

| Version   | MC Version | Voice Chat | Geyser          | Floodgate       | Status |
|-----------|------------|------------|-----------------|-----------------|--------|
| 0.0.1-DEV | 1.21.8     | 2.5.27     | NONE            | NONE            | ❌      |
| 0.0.2-DEV | 1.21.8+    | 2.6.x      | NONE            | NONE            | ❌      |
| 0.0.3-DEV | 1.21.8+    | 2.6.x      | 2.9.0-SNAPSHOT  | 2.2.4-SNAPSHOT+ | ❌      |
| 0.1.0-DEV | 1.20.1+    | 2.6.x      | 2.9.0-SNAPSHOT+ | 2.2.4-SNAPSHOT+ | ⚠️     |
| 0.1.1     | 1.20.1+    | 2.6.x      | 2.9.0-SNAPSHOT+ | 2.2.4-SNAPSHOT+ | ✅      |

### Requirements

* Minecraft 1.20.1+
* Simple Voice Chat 2.6.x+
* GeyserMC 2.9.0-SNAPSHOT+ (Optional)
* Floodgate (optional)

---

## Fabric

| Version   | MC Version | Voice Chat | Geyser          | Floodgate       | LuckPerms             | Status |
|-----------|------------|------------|-----------------|-----------------|-----------------------|--------|
| 0.1.0-DEV | 1.21.11+   | 2.6.x      | 2.9.0-SNAPSHOT+ | 2.2.4-SNAPSHOT+ | compatible w/ 5.4 api | ✅      |
| 0.1.1-DEV | 1.21.11+   | 2.6.x      | 2.9.0-SNAPSHOT+ | 2.2.4-SNAPSHOT+ | compatible w/ 5.4 api | ✅      |

### Requirements

* Fabric Loader (matching your Minecraft version)
* Fabric API (required)
* Simple Voice Chat 2.6.x+

### Optional Integrations

* Floodgate (for Bedrock account linking)
* LuckPerms (for permissions handling)
* GeyserMC 2.9.0-SNAPSHOT+ (for Bedrock compatibility)

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
