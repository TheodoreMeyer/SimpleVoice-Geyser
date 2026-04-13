# Simple Voice Geyser
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.8--1.21.11-blue)](https://www.minecraft.net/en-us/)
[![GitHub release](https://img.shields.io/github/v/release/TheodoreMeyer/SimpleVoice-Geyser?include_prereleases)](https://github.com/TheodoreMeyer/SimpleVoice-Geyser/releases)
[![License](https://img.shields.io/github/license/TheodoreMeyer/SimpleVoice-Geyser)](https://github.com/TheodoreMeyer/SimpleVoice-Geyser/blob/master/LICENSE)

**Supports:**
- [![Spigot](https://img.shields.io/badge/Spigot-Supported-orange?logo=spigotmc&logoColor=white)](https://www.spigotmc.org/resources/132386)
- <a href="https://papermc.io/">
    <img src="https://assets.papermc.io/brand/papermc_logo.min.svg" height="14"/>
    <img src="https://img.shields.io/badge/Paper-Supported-3b82f6"/>
  </a>
-  <a href="https://purpurmc.org/">
    <img src="https://purpurmc.org/docs/images/purpur-small.png" height="14"/>
    <img src="https://img.shields.io/badge/Purpur-Supported-7c3aed"/>
   </a>
- <a href="https://fabricmc.net/">
    <img src="https://fabricmc.net/assets/logo.png" height="14"/>
    <img src="https://img.shields.io/badge/Fabric-Supported-1f2937"/>
  </a>

A Geyser Extension to allow Bedrock Clients to connect with Simple voice Chat.

## What can the Plugin/extension do?
- Allow Bedrock Players to voice chat with Java Players through a web interface.
- Configured to also allow Java Players without the SVC Mod to join the chat.

## Using this Plugin
- Set the '.jar' file into your server's plugin directory.
- Configure it to connect with your server's SimpleVoice chat. See [Installation](https://theodoremeyer.github.io/projects/simplevoicegeyser/install)

### Updating
- Replacing the old jar with the new one.
     - I plan on making config options auto update.

## Documentation
- Our [wiki](https://theodoremeyer.github.io/projects/simplevoicegeyser/) can help!

- old wiki: [wiki](https://github.com/TheodoreMeyer/SimpleVoice-Geyser/wiki).

## Coming Soon
- Dev Release (v 1.1.1-Dev)
- Initial Release (v 1.0.0)

## Features to be worked on
- External Webserver (you host the vc connection at your website)
- Support
  - Fabric
  - Velocity

## Suggestions?
Reach out through issues!

## Dependencies used
- [Geysermc.org](https://geysermc.org)
- [Jetty.org](https://jetty.org)
- SimpleVoiceChat: [ModRepo.de](https://modrepo.de/minecraft/voicechat/overview)
- Bcrypt:          [Mindrot.org](https://www.mindrot.org/projects/jBCrypt/)

## Important Notes
- Simple Voice Chat 2.6.0 or compatible is required to work, It must run on the server.
- GeyserMC 2.9.0-SNAPSHOT or compatible is required to work, It must run on the server.

- Releases/versions/jars ending with -Dev{version} mean this is a dev/test version and not meant for productional use.
   - you are welcome to test these, but no guarantee they will work.
- Microphone/Speaker options may or may not work, depending on your browser, as this was built for Google Chrome.

## Developer Notes
- Please see the contributing.md before contributing to this project.