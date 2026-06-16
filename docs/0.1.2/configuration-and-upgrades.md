---
title: 0.1.2 Configuration And Upgrades
layout: projects
project: simplevoicegeyser
---

# Configuration And Upgrades

Simple Voice Geyser now includes config migration behavior for update-time key additions. The goal is to add missing defaults without overwriting server-owner choices.

## Config Migration

On startup and `/svg reload`, the plugin runs a migration pass that:

1. Loads the current default config keys.
2. Loads the existing server config.
3. Adds missing keys from the defaults.
4. Preserves existing user-set values.
5. Writes a backup before saving migrated config data when supported by the platform implementation.

The migration report includes:

| Field       | Meaning                                                               |
|-------------|-----------------------------------------------------------------------|
| `mode`      | Config format or migration mode, such as YAML/JSON platform behavior. |
| `addedKeys` | Number of missing keys restored from defaults.                        |
| `backup`    | Backup path, or `none` if no backup was created.                      |

## Important Config Keys

| Key                                      | Default                | Notes                                                   |
|------------------------------------------|------------------------|---------------------------------------------------------|
| `server.context-path`                    | `/`                    | Web UI context path for root or subpath deployments.    |
| `server.group.default.force-on-web-join` | `false`                | Forces the default SVG group when a web user joins.     |
| `server.audio.transport-mode`            | `auto`                 | `auto`, `legacy`, or `svg-v2`.                          |
| `server.audio.allow-legacy-fallback`     | `true`                 | Keeps old/unsupported clients working during migration. |
| `updatechecker.enable`                   | `true`                 | Enables startup update checks.                          |
| `config-version`                         | `0.1.1-dev-migration1` | Internal config schema marker.                          |

## `/svg reload`

`/svg reload` reloads the config file, runs migration, applies missing defaults, and reports the migration summary to the command sender.

Some values are read when server components are created and still need a full server restart to take effect. This includes network/server lifecycle settings such as the web server port and bind address.

Use `/svg reload` for config changes that are read dynamically or for restoring missing config keys after editing the config manually. Restart the server after changing listener/web-server binding settings.

## Timeout Policy

`client.vctimeout` remains in the config for compatibility, but timeout enforcement is currently disabled in dev builds. Changing this value should not be expected to change join-time timeout behavior until the maintainer re-enables that feature.

## Upgrade Notes

Keep `server.audio.allow-legacy-fallback` enabled while upgrading mixed client environments. This keeps older or unsupported clients on the legacy transport instead of breaking audio during the `svg-v2` rollout.
