---
title: HTTPS and Reverse Proxies
layout: projects
project: simplevoicegeyser
---

# HTTPS and Reverse Proxies

Browsers only give a page microphone access when it is served over HTTPS, so any voice page that is not on your own computer needs HTTPS. Players then open an address like:

```text
https://voice.example.com
```

instead of:

```text
http://<ip>:8080
```

## Your options

| Setup               | Verdict              |
|---------------------|----------------------|
| Plain HTTP          | Not recommended      |
| Chrome flags        | Temporary workaround |
| HTTPS reverse proxy | The correct solution |

The Chrome flags workaround is described in [Chrome Flags]({% project_link joining/chrome-flags %}).

## Reverse proxy

A reverse proxy such as Caddy or Nginx takes the public HTTPS address and forwards it to the voice page. It must forward web socket upgrades as well as normal requests. Set the plugin's `server.bind-address` to `127.0.0.1` when the proxy runs on the same machine.

Caddy, with the voice page on port `8080`. Caddy gets the certificate by itself:

```text
voice.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

Nginx, inside your HTTPS `server` block:

```text
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_set_header Host $host;
}
```

To serve the page under a path such as `https://example.com/svg/`, see [Proxy and Sub-path Deployment]({% project_link install/proxy-and-subpath %}).

## Velocity networks

Point the reverse proxy at the Velocity plugin's page port (`proxy.port`, `8080` by default), not at a backend. The Velocity plugin can also serve HTTPS itself with `ssl.type` set to `file`. A reverse proxy remains the easier choice when you have certificate chains or want automatic renewal. See [Velocity Proxy Setup]({% project_link velocity %}).
