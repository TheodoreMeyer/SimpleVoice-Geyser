---
title: Installation
layout: projects
project: simplevoicegeyser
---

# Installation

Follow these steps on one server and your players can use voice chat from a browser or the Svg-App.

- Running several servers behind Velocity? Do this page first, then follow [Velocity Proxy Setup]({% project_link velocity %}).
- Using a Minecraft hosting provider? See [Hosting Guides]({% project_link guides %}).

> Simple Voice Geyser depends on Simple Voice Chat and does not work without it.

---

## What you need

### Paper, Purpur and Spigot

* Minecraft **1.20.1 through 26.3**
* Simple Voice Chat 2.6.x or newer (2.6.24 or newer on Minecraft 26.3)
* GeyserMC 2.10.1-SNAPSHOT or newer, for Bedrock players
* Floodgate (optional, recommended)

### Fabric

* Minecraft 26.1 or newer, with a matching Fabric Loader
* Fabric API
* Simple Voice Chat 2.6.x or newer
* GeyserMC 2.10.1-SNAPSHOT or newer, for Bedrock players
* LuckPerms and Floodgate (optional)

The exact versions are in [Compatibility]({% project_link install/compatibility %}).

---

## Step 1: Install

1. Download the latest release from [Modrinth](https://modrinth.com/plugin/simplevoice-geyser) or [GitHub](https://github.com/TheodoreMeyer/SimpleVoice-Geyser/releases).
2. Put the `.jar` in `plugins/` (Paper, Purpur, Spigot) or `mods/` (Fabric).
3. Make sure Simple Voice Chat is installed. Add GeyserMC if you have Bedrock players.
4. Start or restart the server.
5. Check the console: no errors, and Simple Voice Chat loaded.

---

## Step 2: Open the ports

Players need to reach two things. Allow both in your firewall and port forwarding.

| What                         | Protocol | Default port                      |
|------------------------------|----------|-----------------------------------|
| The voice page (this plugin) | TCP      | `8080` (`server.port`)            |
| Simple Voice Chat            | UDP      | `24454` (Simple Voice Chat's own) |

The voice page port is not the Minecraft port. Simple Voice Chat sends the voice over UDP, so with only TCP open the page loads but nobody hears anything. If the Simple Voice Chat mod shows a crossed-out speaker, the UDP port is closed.

---

## Step 3: Give players the address

Players need the address of the voice page. The plugin cannot know your public address, so tell them, or put it in the join message ([how]({% project_link install/config %})).

- Browsers only allow the microphone on HTTPS pages. For anything beyond your own computer, put HTTPS in front of the page. See [HTTPS and Reverse Proxies]({% project_link install/proxy %}).
- On a Velocity network, the proxy hosts the page. See [Velocity Proxy Setup]({% project_link velocity %}).

---

## Step 4: Test it

1. Join the server and run `/svg pswd <password>` (8 to 32 characters). On Paper, Purpur and Spigot, end the command with `confirm`.
2. Open the voice page, type your Minecraft name and that password, and click **Join**. Allow the microphone.
3. Ask a second player to join the same way, or a Java player with the Simple Voice Chat mod, and talk both ways.

If something fails, see [Troubleshooting]({% project_link troubleshooting %}).

---

## After setup

* Change a setting, such as who may join or the default group password: [Configuration]({% project_link install/config %}).
* Lock the setup down: [Security]({% project_link install/security %}).
* Update to a newer version: [Upgrading]({% project_link install/upgrading %}).

Players who run the Simple Voice Chat mod use the mod, not this page. Versions marked `-DEV` are not ready for production.
