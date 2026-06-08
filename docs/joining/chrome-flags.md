---
layout: projects
title: Chrome Flags
sidebar: simplevoicegeyser
permalink: /projects/simplevoicegeyser/join/chrome-flags/
---

# Fix: Insecure Context (HTTP)

## Problem

Voice chat may not work over `http://` connections.

This is because modern browsers block microphone access on insecure origins.

---

## Solution (Chrome)

1. Open Chrome and go to:

   chrome://flags/#unsafely-treat-insecure-origin-as-secure

2. Enable the setting

3. In the input field, add your server address:

   http:// ip: port

   Example: http://192.168.1.10:8080


---

## Important Notes

- You **must include**:
- `http://`
- the correct port
- Multiple entries must be separated by commas
- This is a workaround, not a long-term solution