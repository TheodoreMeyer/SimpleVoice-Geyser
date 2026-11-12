---
title: Upgrading
layout: projects
project: simplevoicegeyser
---

# Upgrading

This page documents version upgrade behavior and breaking changes.

---

## General Rules

- Upgrading to newer versions is supported unless stated otherwise
- Downgrading is **not supported**
- Always back up your server before upgrading

---

## Version-Specific Notes

### Upgrade to 0.1.5

- No password reset is needed. Passwords from 0.1.4 keep working.
- New options are added to your config automatically when the server starts, and a backup of the old file is kept. The new options are `server.audio.mic.allowLegacy` and the `proxy` section.
- The Velocity proxy plugin is new and optional. See [Velocity Proxy Setup]({% project_link velocity %}).
- If you run a Velocity proxy, use the same version on the proxy and on every backend.
- Svg-App 1.1.0 works with 0.1.3, 0.1.4 and 0.1.5 servers. See [Svg-App]({% project_link app %}).

### Upgrade to 0.0.3-DEV

- Password storage behavior changed
- Passwords are no longer persisted in plaintext
- Users must reset passwords after upgrading

#### Impact

- Existing users will lose access until passwords are reconfigured
- Improves overall security

### Upgrade to 0.1.1

- Password storage behavior changed
- Users must reset passwords after upgrading

#### Impact

- Existing users will lose access until passwords are reconfigured
- This removes redundancy and increases performance.

---

## Best Practices

- Test upgrades in a staging environment
- Read changelogs before updating
- Avoid upgrading production servers blindly

---

## DEV Builds

- `-DEV` versions are experimental
- Expect breaking changes
- Not recommended for production use