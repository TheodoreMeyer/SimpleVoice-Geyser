
# Playit Guide
- By [SButzbach07](https://github.com/SButzbach07)

## Known limitations
Browsers require HTTPS to properly allow microphone and speaker access with SVG. However, SVG doesn't natively host its web interface via HTTPS. This poses problems. We'd need a reverse proxy to expose an HTTPS tunnel with a domain to the public, and another reverse proxy to terminate SSL from the tunnel to SVG.

Easiest way is a Playit + Caddy combo, but an HTTPS tunnel with Playit is exclusive to Premium users only. Caddy is only there to SSL-terminate. So, unless you can find a free reverse proxy service that provides HTTPS tunnels and domains, you're basically screwed there. Cloudflare or Tailscale might be able to help, but I never tried either of those.

---
## Setup
For my configuration, I run a Fabric 26.2 server with Geyser, Simple Voice Chat, and SimpleVoice-Geyser, all configured with VoxelDash One since I run multiple Minecraft servers. I also have Floodgate and Hydraulic installed, but those mods are irrelevant to this guide, as well as other unmentioned mods I have to try to unify the Java/Bedrock player experience. Assume everything is up-to-date.

## Step 1: Check your `config.json` file on the Minecraft server
Open `config/SimpleVoice-Geyser/config.json` on your Minecraft server.
<img width="490" height="452" alt="Image" src="https://github.com/user-attachments/assets/51d6a36a-bf9c-4e36-8df0-956abcb85c33" />

In the `server` object, set `bind-address` to 0.0.0.0 and `port` to 8080. Those should be the default values anyway, so no need to touch them.
<img width="353" height="433" alt="Image" src="https://github.com/user-attachments/assets/0fc91316-d372-445a-a30e-7f4b7ef6f7a8" />
Restart your Minecraft server if you made any changes.

## Step 2: Set up an HTTPS tunnel in Playit
Players need to connect to the web interface on the same server SVG runs on to properly use voice chat, and we already know they need to connect via HTTPS.

You will need a Premium Playit plan for this method, but they're pretty cheap (I use the annually-billed $30 plan). This allows you to create HTTPS tunnels, create up to three custom *.playit.plus* domains for free, and choose where traffic gets routed for your tunnels through the Premium Network. In my opinion, it's very worth it.

An HTTPS tunnel in Playit requires a gateway. Head to Account, and click Create Gateway.
<img width="380" height="406" alt="Image" src="https://github.com/user-attachments/assets/aeb11213-f4b7-4b2c-972e-9d0201aa0ad0" />

Select what region you want traffic to route through, preferrably between where you're hosting your Minecraft server at, and where the majority of your players are connecting from.
<img width="1194" height="716" alt="Image" src="https://github.com/user-attachments/assets/84acde79-9dc8-4e97-9c96-e402628f6e5f" />
I like the North America option, even though it's pretty much the United States. Regardless, all (or almost all) of my friends reside in the States, so traffic gets routed throughout those servers in that region.

Next, either do the whole DNS thing to connect an external domain to the gateway, or create one with Playit (easier option). Premium users can create up to three custom *.playit.plus* domains for free.
<img width="1106" height="625" alt="Image" src="https://github.com/user-attachments/assets/e0597ea0-35e0-4555-9dc9-09b2f0f5941e" />

If you already have a domain registered with Playit, you can create subdomains with it to add more tunnels if needed. Head to Account, and click List Domains.
<img width="382" height="334" alt="Image" src="https://github.com/user-attachments/assets/5d11e73d-09e6-4d88-b749-04c74a95a9da" />

Select your domain, head down to Create Subdomain, type in a name for your new subdomain, and click Create (not shown in photo, but it's on the right of the textbox)
<img width="514" height="530" alt="Image" src="https://github.com/user-attachments/assets/7907ac32-47b6-4d5d-8b5e-9048de35d2be" />
As you can see I have multiple subdomains set here for different tunnels. You can probably tell which one I use for my SVG tunnel.

Once a domain is assigned to the gateway, we need to create our HTTPS tunnel. Head to Tunnels, create a new tunnel, and select your new gateway-connected domain. If you have an unused domain, you can use that and create a new gateway from there.
<img width="886" height="899" alt="Image" src="https://github.com/user-attachments/assets/bed321df-a69d-48d7-91ee-28115834fe82" />
In the photo, *Unknown* is a gateway without a domain. Custom and external domains are preferred because I've seen Playit-assigned domain names change, specifically with the Simple Voice Chat tunnels I have.

Assign the tunnel to your agent (homemc is my agent for this example), keep Http Port at 80, Https Port at 443, Local IP at 127.0.0.1 since SVG is running locally, and leave Proxy Protocol unchanged (should be None). Click Create Tunnel.
<img width="448" height="705" alt="Image" src="https://github.com/user-attachments/assets/6268d153-a355-4bb5-9554-71704c77089a" />
Copy the public address of your new tunnel because you will need it for this next step.

## Step 3: Install and Configure Caddy
Caddy is a useful local web server with built-in HTTPS support and SSL-termination. I use it not only for my SVG tunnel, but I also configured Caddy to also SSL-terminate two Polymer AutoHost tunnels required for my Geyser server and another Minecraft server I also use Polymer with.

We're only here to connect our new Playit tunnel with SVG, which means we need to install Caddy on the same machine SVG is running on so Caddy can expose it. If you're on Windows, head to https://caddyserver.com/download, download the executable, and run it to install it. On Linux, use whatever package manager you use to install Caddy. In the case of Debian (what I use for my servers), run `sudo apt-get install caddy`.

We need to open the Caddyfile to configure Caddy.
I don't know where it's at on Windows, so good luck finding it. Once you do, open it with whatever text editor you use (Notepad, VSCode, etc.)
On Linux, it's normally located at `/etc/caddy/Caddyfile`. Open it with your default text editor, preferrably as root (I like using Nano).

Feel free to delete everything in the file and type the following:
```text
<Your Playit tunnel domain> {
    reverse_proxy 127.0.0.1:8080
}
```
Replace `<Your Playit tunnel domain>` with whatever domain you have set. Save and close the file, then restart Caddy.

## Step 4: Testing your configuration
With everything configured correctly, open your tunnel domain in a compatible browser (it works fine in Opera GX btw, meaning regular Opera works as well), and you should see the SVG web interface with microphone and speaker permissions allowed. Join your Minecraft server in Bedrock, do the whole `/svg pswd [pswd between 8-32 characters]` command, enter your username and password, make sure your microphone and speakers are set correctly, and join.

You may need to finesse it a few times to get it to connect. Some players may sound very statickly, so have them refresh the page and join again. That is not my job to fix, or it can't be fixed idk. I'm only here to help you get this working.

Hope this helps so this issue can get closed.