---
title: Troubleshooting
layout: projects
project: simplevoicegeyser
---

# Troubleshooting

Find the message or symptom you see, then read the cause. Please look through the docs and earlier issues before opening a new one.

## The voice page will not open

- Use the voice page's port, not the Minecraft port. The default is `8080`.
- Check that the port is open for **TCP** in your firewall and port forwarding, and that nothing else uses it.
- Check `server.bind-address`. `127.0.0.1` only allows the machine itself.
- The page is blank or the microphone is blocked: browsers need HTTPS. See [HTTPS and Reverse Proxies]({% project_link install/proxy %}).

## The server's voice page does not start

- The port is already in use by another program or another server. Pick another `server.port`. Two backends on one machine need different ports.

## Messages when signing in

- **"Access Denied: Invalid username or password."** The player has not set a password, or typed the name or password wrong. Bedrock players who join through Floodgate must include the prefix in their name (Floodgate's default is a dot), exactly as the server shows it.
- **"Too many failed login attempts."** The player is locked out for a few minutes. See [Security]({% project_link install/security %}).
- **"Timeout: You didn't join the server in time."** The player is not in the game. Join the Minecraft server first, then sign in.
- **"Access Denied. You may have been banned from vc."** The player does not have the permission `svg.vc.join`. See [Commands]({% project_link commands %}).
- **"Access Denied: Please remove the Simple Voice Chat mod."** The player has the Simple Voice Chat mod installed. Players with the mod use it directly, not the voice page.
- **"Access Denied: You must be a Bedrock player to join!"** The server has `client.requireBedrock` turned on.
- **"Unsupported client type."** The server blocks that client. See [Who Can Join]({% project_link joining/clients %}).
- **"Update required" or "Outdated client."** Refresh the page. On a Velocity network the proxy and every backend must run the same version.
- **"This server requires Opus microphone audio."** The server has `server.audio.mic.allowLegacy` turned off and the client can only send the older PCM format. Update the client.
- **"Voice chat server unavailable."** Simple Voice Chat is not running properly on that server. Check its log.

## Connected, but there is a problem

- **Nobody can hear anyone, or the Java client shows the crossed-out speaker.** The Simple Voice Chat UDP port is closed. Open it for **UDP**. See [Installation]({% project_link install %}).
- **Players get disconnected after a while.** A connection with no voice traffic is closed after `client.idletimeout` minutes (2 by default, up to 10). On a Velocity network the proxy's limit is a fixed 2 minutes.
- **Leaving the Minecraft server ends the voice connection.** Join the server again, then sign in again.
- **Voice sounds buggy.** Make sure you have a good connection. If voice sounds **robotic or distorted** over a remote or WAN connection, see [Audio Transport]({% project_link install/audio-transport %}).
- **The Svg-App shows its own message.** See the table in [Svg-App]({% project_link app %}).

## Several servers (Velocity)

See the troubleshooting list in [Velocity Proxy Setup]({% project_link velocity %}).

## Opening an issue

* Ensure all dependencies match supported versions
* Check server logs for startup errors
* Verify Geyser and Simple Voice Chat are functioning independently

If issues persist, open an issue on the GitHub repository with:

* server type (Paper / Fabric / Velocity, etc.)
* plugin versions
* logs
    * client console
    * Server logs
    * client chatbox
    * Optional: debug logs on server
    * PLEASE NOTE: DO NOT share private information like usernames, passwords, and others in logs.
