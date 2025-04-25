# Simple Voice Geyser
A Geyser Extension to allow bedrock clients to connect with Simple voice Chat

## What can the Plugin/extension do?
- Allow Bedrock Players to voice chat with Java Players through a web interface
- Configured to also allow Java Players without the Mod join the chat.

## Using this Plugin
- Set into the Plugins folder of your Spigot Server directory.
- Configure it to connect with your server's SimpleVoice chat. See [our wiki](https://github.com/TheodoreMeyer/SimpleVoice-Geyser/wiki)
- Players can now join from the web broswer after using /svg pswd {password} in-game!
- update by replacing the old jar with the new one.

## Documentation
Our [wiki](https://github.com/TheodoreMeyer/SimpleVoice-Geyser/wiki) can help!

## Coming Soon
- Inital Release (v 1.0.0)

## Features to be worked on
- Permissions
- Finish audio connection
- Simple Voice Chat group support
- Admin commands
- Better Html page
- External Webserver (you host the vc connection at your website)

## Suggestions?
Reach out through issues!

## Dependencies used
- GeyserMc         [GeyserMc.org](geysermc.org)
- Jetty            [Jetty.org](jetty.org)
- SimpleVoiceChat  [Modrepo.de](https://modrepo.de/minecraft/voicechat/overview)

## Dependencies docs:
- [Jetty Server Docs](https://jetty.org/docs/jetty/11/index.html)
- [Geyser API introduction](https://wiki.geysermc.org/geyser/api/)
- [Introduction to Events](https://wiki.geysermc.org/geyser/events).
- [Simple Voice Chat Api Guide](https://modrepo.de/minecraft/voicechat/api/overview)

## Important Notes
- `Simple Voice Chat is required to work, It must run on the server.
- This is built as a Spigot Plugin that requires GeyserMc Spigot to connect bedrock Clients
- Releases/versions/jars ending with -Dev{version} mean this is a dev/test version and not meant for productional use.
   - you are welcome to test these, but no gurantee they will work.

