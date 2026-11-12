---
title: Joining via SVG
layout: projects
project: simplevoicegeyser
---

# Joining the Voice Chat

To join the voice chat you need three things: to be in the game, a voice password, and the address of your server's voice page. On a phone you can use the [Svg-App]({% project_link app %}) instead of the page.

## Before you join

1. **Join the Minecraft server and stay on it.** The voice chat is tied to your game. If you leave the server, you are disconnected from the voice chat.
2. **Set your voice password** in the game with `/svg pswd <password>`. It must be 8 to 32 characters.
   - On Paper, Purpur and Spigot servers end the command with `confirm`: `/svg pswd <password> confirm`.
   - On Fabric servers and on Velocity networks, do not add `confirm`.
   - Bedrock players can run `/svg` and use the form, which keeps the password out of the chat.
3. **Get the address of the voice page from your server admin.** It is not the address you use in Minecraft. Admins can find how to set it up in [Proxy and Sub-path Deployment]({% project_link install/proxy-and-subpath %}).

Every player is allowed to join the voice chat by default. If an admin has taken that permission away, you will see "You may have been banned from vc."

## Join

1. Open the voice page in your browser.
2. Type your Minecraft name and your voice password. Bedrock players who joined through Floodgate must type the name with its prefix, for example `.Steve`, exactly as the server shows it.
3. Click **Join** and allow the microphone when the browser asks.

If the browser will not give the page the microphone, the page is probably on plain HTTP. Ask your admin for an `https` address.

You join a group called `Svg`, which players on the web page and the app share. Admins can change it in the config.

## Using the page

- **Transmit mode:** choose Voice Activation, where the page sends your voice when you speak, or Push-To-Talk.
- **Push-To-Talk:** hold the on-screen **Hold to Talk** button, or bind a keyboard key or a controller button with **Bind Push-to-Talk**. **Fullscreen PTT** gives you one large button, which suits phones and consoles. **Allow background controller PTT** lets a controller button work while the page is in the background.
- **Mute, microphone and speaker:** the page lists your microphones and speakers. **Test Sound** plays a sound so you can check your speaker.
- **Chat:** type in the message box to write in the game chat.
- **Language:** the language button switches between English, Español, Italiano and Polski.

If nobody speaks for a while, the server can disconnect you. By default this happens after 2 minutes. Admins can change it with `client.idletimeout`.

## Where is the page?

Your admin tells you the address. If you are the admin and want to try it yourself:

| Where you are | Address |
|---------------|---------|
| On the server's computer | `http://127.0.0.1:8080` |
| On the same network | `http://<server ip>:8080` |
| Somewhere else | Your public address |

The number `8080` is the default page port and may be different on your server.

For players outside your network, open the page port for **TCP** and the Simple Voice Chat port for **UDP**, or use a tunnel service such as playit.gg. The page uses TCP, but Simple Voice Chat sends the voice itself over UDP, so the voice chat only works if both are open. See [Installation]({% project_link install %}) and [Playit]({% project_link guides/playit %}).

## Other guides

- [Chrome Flags]({% project_link joining/chrome-flags %}): a temporary workaround for browsers that block the microphone on HTTP.
- [Who Can Join]({% project_link joining/clients %}): for admins who want to allow only the page or only the app.
