---
title: Security
layout: projects
project: simplevoicegeyser
---

# Security

This plugin exposes a web-based voice interface. You are responsible for securing access.

---

# Built-in protections:
- Passwords are hashed (bcrypt)
- Every player sets their own password. There are no default passwords for logins.
- Repeated wrong passwords lock the player out for a while. By default, 5 failed logins within 3 minutes lock the player out for 8 minutes. The numbers are `server.security.max-auth-failures`, `auth-fail-duration` and `auth-lock-duration`.
     - To get back in, the player waits for the lockout to end. On a backend server, setting a new password with `/svg pswd` also clears it.
     - On a Velocity network the lockout is held by the proxy and ends after its time.
- Joining voice needs the permission `svg.vc.join`, which players have by default. Take it away to keep a player out of voice chat. See [Commands]({% project_link commands %}).

---

## Key Risks

### 1. Unencrypted Traffic

- Audio is transmitted over **HTTP (not HTTPS)**
- Data is not encrypted in transit
- Anyone on the same network can potentially intercept traffic

---

### 2. Browser Restrictions

- Microphone access requires a **secure context (HTTPS)**
- HTTP connections may fail or require unsafe browser overrides

---

### 3. Password Security

- Passwords are hashed, but:
  - Weak passwords can still be brute-forced

---

## Recommendations

### Use HTTPS (Strongly Recommended)

Set up a reverse proxy (e.g. Nginx, Caddy):

- Enables HTTPS
- Fixes browser microphone restrictions
- Encrypts traffic

---

### Use Strong Passwords

- 8 to 32 characters
- Avoid defaults
- Rotate periodically if exposed publicly

---

### Change the Default Group Password

Players who join through the web page or the app are put in a group named `Svg` with the password `1a2b`. Anyone can read that password in these docs. Change `server.group.default.password`, or turn the default group off with `server.group.default.enabled`.

---

### Protect the Proxy Secret

On a Velocity network the proxy and every backend share `proxy.shared_secret`. Anyone who has it can pretend to be any player. Keep it private, and change it everywhere if it leaks. See [Velocity Proxy Setup]({% project_link velocity %}).

---

### Restrict Exposure

- Use firewall rules where possible
- Avoid exposing the service unnecessarily

---

## For Server Owners

If your server is public:

- Do NOT rely on HTTP
- Do NOT use default credentials
- Assume traffic can be inspected without HTTPS

---

## Summary

| Risk                 | Severity |
|----------------------|----------|
| HTTP (no encryption) | High     |
| Weak passwords       | Medium   |
| Browser blocking     | High     |

Proper deployment should include HTTPS and controlled access.