---
title: Simple Voice Geyser
layout: projects
project: simplevoicegeyser
---

# Simple Voice Geyser

Simple Voice Chat is a Minecraft mod that gives players proximity voice chat: you hear people who are near you in the game. It needs a mod on the player's computer, so players on Bedrock Edition, on a phone or console, or on Java without mods are left out.

Simple Voice Geyser fixes that. It is a plugin or mod for your server that lets those players take part in the same voice chat through a web page in their browser, or through the Svg-App on Android. They hear Java players and Java players hear them, with the same distance and direction effects.

## Three ways to join

| Player | How they join |
|--------|---------------|
| Bedrock player (through Geyser) | Opens the server's voice web page in a browser, or uses the [Svg-App]({% project_link app %}) on Android |
| Java player without the Simple Voice Chat mod | Same web page or app |
| Java player with the Simple Voice Chat mod | Nothing to do, the mod already works. Simple Voice Geyser does not replace it |

You decide which doors are open. You can allow the browser, the app, or both. See [Who can join]({% project_link joining/clients %}).

## What you need

- A Paper, Purpur, Spigot or Fabric server with [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) 2.6 or newer installed on it.
- Geyser (and Floodgate, which is recommended) if you have Bedrock players.
- Players need Chrome or another recent browser, or an Android phone with Android 7 or newer for the app.
- A web address for the voice page. For anything beyond your own home network this should be HTTPS, because browsers refuse to give microphone access to plain HTTP pages. See [HTTPS and reverse proxies]({% project_link install/proxy %}).

Exact Minecraft versions are in [Compatibility]({% project_link install/compatibility %}).

## Set it up in five steps

1. Put the Simple Voice Geyser jar in your server's `plugins` folder (Fabric: `mods`) and restart.
2. Check the console shows no errors and that Simple Voice Chat loaded first.
3. Make sure the web port (default `8080`) is reachable by your players. This is not the Minecraft port.
4. Tell your players the address of the voice page.
5. Optional but recommended: put HTTPS in front of it. See [Installation]({% project_link install %}).

Running several servers behind Velocity? Use the proxy plugin so players keep their voice connection when they change server: [Velocity proxy setup]({% project_link velocity %}).

## What a player does

1. In the game, run `/svg pswd <password>` (8 to 32 characters). On Paper, Purpur and Spigot servers the command must end with the word `confirm`: `/svg pswd <password> confirm`. Bedrock players can run `/svg` and use the form, which keeps the password out of the chat.
2. Stay in the game, open the voice page or the app, type the Minecraft name (Bedrock players include their Floodgate prefix) and that password, and join. Leaving the game ends the voice connection.
3. Allow the microphone when the browser or phone asks, then talk.

More in [Joining]({% project_link joining %}).

## Need help

[Troubleshooting]({% project_link troubleshooting %}) lists common problems by what you see. For anything else, open an issue on [GitHub](https://github.com/TheodoreMeyer/SimpleVoice-Geyser).
