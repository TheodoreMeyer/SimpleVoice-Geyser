---
title: Svg-App for Android
layout: projects
project: simplevoicegeyser
---

# Svg-App for Android

Svg-App is the companion app for Simple Voice Geyser on Android. It lets you join a server's voice chat from your phone. You hear the players near you in the game, they hear you, and you can read and send messages to the in-game chat. It is the phone version of the voice web page, so it is the easy option for Bedrock players on a phone who are playing on a Simple Voice Geyser server.

## What you need

- An Android phone with Android 7.0 or newer.
- A server that uses Simple Voice Geyser 0.1.3, 0.1.4 or 0.1.5, and that allows the app. Ask the server owner, or read [Who can join]({% project_link joining/clients %}).
- The address of the server's voice page. This is **not** the address you type into Minecraft. Your server owner will give it to you.
- A password for the voice chat, set in the game (see below).

## Install

1. Open the [Svg-App releases page](https://github.com/TheodoreMeyer/Svg-App/releases) on your phone and download the latest `.apk` file.
2. Open the file and allow your phone to install apps from your browser if it asks.
3. Open Svg-App.

Upgrading from 1.0.2 to 1.1.0 is a one-time exception. Version 1.1.0 installs as a second app next to 1.0.2, and your saved servers and passwords are not copied over. Remove the old app first, then enter your server again. Later updates install over the top as normal.

## Set your password in the game

Before the first sign-in, join the Minecraft server and run:

```
/svg pswd <your-password>
```

The password must be 8 to 32 characters. On Paper, Purpur and Spigot servers the command must end with the word `confirm`: `/svg pswd <password> confirm`. Servers behind a Velocity proxy and Fabric servers do not use `confirm`. Bedrock players can also type `/svg` and use the form that opens, which keeps the password out of the chat. This password is only for voice chat, so you do not have to use your Minecraft account password.

## Connect

1. Join the Minecraft server and stay on it. If you leave the server, the app disconnects too.
2. In Svg-App, type the server's voice address, your Minecraft name and your password. Use your name exactly as it appears on the server. Bedrock players who joined through Floodgate include the prefix, for example `.Steve`.
3. Tap **Join**. Allow the microphone when your phone asks.
4. Choose **Open mic** to talk freely, or push-to-talk and hold the button when you want to speak.

How to type the address:

| Your server's voice page | Type in the app |
|--------------------------|-----------------|
| `https://voice.example.com` | `voice.example.com` |
| `https://example.com/svg` | `example.com/svg` |
| `http://192.168.1.50:8080` (home network) | `http://192.168.1.50:8080` |

The app assumes `https` when you do not type anything in front. A home-network address without HTTPS therefore needs `http://` at the start. Always include the port when the address needs one, and use the voice page's port, not the Minecraft port. If you type the Minecraft port, the app warns you.

## Using it

- **Voice tab:** your connection, who you are signed in as, the mute button, push-to-talk, and the choice between the speaker and the phone earpiece.
- **Chat tab:** read the server chat and send messages.
- **Test audio:** on the sign-in screen, play your own microphone back before you join.
- **Notification:** while you are in a call you can mute, change the audio output, reply to chat or leave from the notification.
- **Background:** push-to-talk stops when the app is not on screen. Tap **Unmute** in the notification to switch to open mic.
- **Language:** English, Polski, Italiano and Español. It follows your phone's language, and you can change it at any time without leaving the call.

## On a server network

If the server uses a Velocity proxy, use the proxy's voice address. When you move to another server in the network the app shows "Changing server" and then carries on, without asking you to sign in again.

## If something goes wrong

| What the app says | What to do |
|-------------------|------------|
| "That looks like the minecraft server port" | Use the voice page's port instead of the Minecraft port |
| "Could not connect to the server" | Check the address, and that the server is running |
| "Could not establish a secure connection" | Use the server's `https` address, or type `http://` yourself if you are on your own network |
| "The server rejected the login" | Check your name and password. You may need to set the password again with `/svg pswd` |
| "This app version is not compatible with this server. Update the app." | Install the latest Svg-App, or ask the server owner which version they run |
| "This server does not allow the Svg-App client" | The server owner has switched the app off |
| "This server requires Opus microphone audio" | Your phone cannot encode Opus. Use a newer phone (Android 10 or newer), or ask the server owner to allow older microphone formats |
| "Microphone permission was denied" | Allow the microphone for Svg-App in your phone's settings |
| "No internet connection" | Check your Wi-Fi or mobile data |

If the app loses the connection it tries to reconnect a few times on its own. The server also disconnects a quiet connection after 2 minutes by default, and admins can change that.

## How it works

The app talks to the same voice server as the web page. It sends your microphone sound to the server, the server hands it to Simple Voice Chat, and Simple Voice Chat plays it to the players near you. The sound of nearby players comes back to your phone the same way. On servers on version 0.1.5 the app can send compressed audio if your phone supports it (usually Android 10 or newer), and falls back to an older format if the server allows that.

Developers can find the join message and error codes in the [protocol reference]({% project_link api/protocol %}).
