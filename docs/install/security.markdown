---
title: Security
---

# Security

This plugin exposes a web-based voice interface. You are responsible for securing access.

---

# Built-in protections:
- Passwords are hashed (bcrypt)
- No default credentials
- If someone spams logins for a player (more than 5 per minute), that player is temporarily locked out for 5 minutes.
     - To get back in, the player must wait 5 minutes or change their password in-game (which resets the lockout)

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

- Minimum 8+ characters
- Avoid defaults
- Rotate periodically if exposed publicly

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