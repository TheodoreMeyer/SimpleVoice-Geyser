---
title: HTTPS and Reverse Proxies
layout: projects
project: simplevoicegeyser
---

# HTTPS and Reverse Proxies

Browsers only give a page the microphone when it is served over HTTPS, so any voice page that is not on your own computer needs HTTPS. The goal of this page: players open `https://voice.example.com` and the microphone works.

| Setup               | Verdict              |
|---------------------|----------------------|
| Plain HTTP          | Not recommended      |
| Chrome flags        | Temporary workaround |
| HTTPS reverse proxy | The correct solution |

The Chrome flags workaround is in [Chrome Flags]({% project_link joining/chrome-flags %}).

---

## Put HTTPS in front of the voice page

A reverse proxy such as Caddy or Nginx takes the public HTTPS address and forwards it to the voice page. You need a domain that points at your server.

1. Open the plugin's config file and set `server.bind-address` to `127.0.0.1` if the proxy runs on the same machine. Restart the server. ([Configuration]({% project_link install/config %}))
2. Add this to your Caddyfile. Caddy gets the certificate by itself:

   ```text
   voice.example.com {
       reverse_proxy 127.0.0.1:8080
   }
   ```

3. Reload Caddy.
4. Check it: open `https://voice.example.com`. The voice page loads, and the browser asks for the microphone when you join.

If the page loads but joining fails, the proxy is probably not forwarding WebSocket upgrades.

{% dropdownStart Using Nginx instead of Caddy %}

Do steps 1, 3 and 4 the same way. For step 2, put this inside your HTTPS `server` block. The two `Upgrade` lines let the voice connection (a WebSocket) through.

```text
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
}
```

{% dropdownEnd %}

---

## Put the page under a path

Use this when the page shares a domain with a website, for example `https://example.com/svg/`.

1. In the plugin's config file, set `server.context-path` to `/svg`, then restart the server.
2. In the proxy, forward `/svg/` to the voice page without removing `/svg` from the path.
3. Check it: open `https://example.com/svg/`.

The path in the proxy and `server.context-path` must be the same. If they differ, the page's files or the voice connection fail to load. Forward WebSocket upgrades here too, and use `https` and `wss` for a public server.

---

## Velocity networks

Point the reverse proxy at the Velocity plugin's page port (`proxy.port`, `8080` by default), not at a backend. The Velocity plugin can also serve HTTPS itself, but a reverse proxy is easier when you have a certificate chain or want automatic renewal. See [Velocity Proxy Setup]({% project_link velocity %}).
