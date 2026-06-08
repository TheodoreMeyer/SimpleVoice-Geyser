---
title: HTTPS / Reverse Proxy Setup
---

# HTTPS / Reverse Proxy Setup

This is the **recommended production setup**.

Without HTTPS:
- Microphone access may fail
- Audio is unencrypted
- Browsers may block functionality

---

## Why HTTPS is Required

Modern browsers require a **secure context** for:
- Microphone access
- WebRTC functionality

HTTP (`http://`) is considered insecure.

---

## Recommended Approach

Run SimpleVoiceGeyser on: 127.0.0.1:8080 (HTTP)

Then expose it via a reverse proxy:

https://yourdomain.com


---

## Example: Nginx Configuration

```nginx
server {
    listen 443 ssl;
    server_name yourdomain.com;

    ssl_certificate /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    location / {
        proxy_pass http://127.0.0.1:8080;

        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        proxy_set_header Host $host;
        
        # Enforce connections and request rate ceilings
        limit_conn websocket_conn_limit 5;
        limit_req zone=websocket_req_limit burst=10 nodelay;
    }
}
```

### Important Notes
WebSocket support must be enabled

Do NOT remove:

proxy_set_header Upgrade
proxy_set_header Connection
SSL Certificates

##Use Let's Encrypt:

- Free
- Auto-renewable
- Works with Nginx, Caddy, etc.

## Alternative: Caddy (Simpler)
yourdomain.com {
    reverse_proxy localhost:8080
}

Caddy automatically handles HTTPS.

Result

Users connect via:

https://yourdomain.com

Instead of:

http://<ip>:8080

## Summary
Setup Type-Status:

HTTP only-Not recommended
Chrome flags-Temporary workaround
HTTPS proxy-Correct solution