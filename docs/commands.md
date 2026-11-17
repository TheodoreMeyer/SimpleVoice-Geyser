---
title: Commands
layout: projects
project: simplevoicegeyser
---

# SimpleVoiceGeyser Commands

All commands use the base command: `/svg`

---

## /svg

* Java Players: Displays help
* Bedrock Players: Opens the command menu (if supported by Geyser/Floodgate)

---

## /svg pswd

Sets your personal voice chat password.

### Usage

```
/svg pswd <new-password>
```

### Details

* Must be executed by a player
* Password length must be between 8 and 32 characters
* Used to sign in to the voice web page and the Svg-App

### NOTE
- On Paper, Purpur and Spigot servers the command must end with `confirm`: `/svg pswd <new-password> confirm`. Without it the command is refused and the player is told to add it.
- On Fabric servers, and on networks that use the Velocity proxy, do not add `confirm`. On a Velocity network it would become part of the password.
- Passwords typed in a command can show up in the console and logs. Bedrock players can run `/svg pswd` or `/svg` without a password to use a form instead.
- On a Velocity network the password is stored on the proxy and works for every backend.

---

## /svg cgroup

Creates a new voice group.

### Usage

```
/svg cgroup <group-name> [-t type] [-p password] [-ps]
```

### Arguments

* `<group-name>`

  * Required
  * Name of the group

### Flags

* `-t <type>`

  * Optional
  * Group type:

    * `open` (default)
    * `normal`
    * `isolated`

* `-p <password>`

  * Optional
  * Sets a password for the group

* `-ps`

  * Optional
  * Marks the group as persistent
  * Persistent groups remain after all players leave

### Defaults

If not specified:

* `type = open`
* `password = ""`
* `persistent = false`

### Permissions

* `svg.vc.group.create`
* `svg.vc.group.type.isolated`
* `svg.vc.group.setpersistent`

---

## /svg jgroup

Joins an existing voice group.

### Usage

```
/svg jgroup <group-name> [password]
```

### Arguments

* `<group-name>`

  * Required
  * Name of the group to join

* `[password]`

  * Optional
  * Required if the group is password protected

---

## /svg lgroup

Leaves the current voice group.

### Usage

```
/svg lgroup
```

### Details

* Must be executed by a player
* Immediately removes the player from their current group

---

## /svg reload

* Reloads the plugin configuration. Please note that this does not reload everything.
* Requires * `svg.admin` permission.
---

## /svg checkUpdate

* Triggers an update check and notifies the console if an update is available.
* Requires * `svg.admin` permission.

---

## /svg help

Displays the command help menu.

### Usage

```
/svg help
```

---

## Permissions

- `svg.vc.join`: needed to join the voice chat from the web page or the app. **Every player has it by default.** Take it away from a player, for example with LuckPerms, to keep them out of voice chat. They see "You may have been banned from vc."
- `svg.admin`: admin commands (`/svg reload`, `/svg checkUpdate`) and the admin menu. Ops by default.
- `svg.vc.group.create`: create a group. Every player has it by default.
- `svg.vc.group.join`: join a group. If players cannot join groups, grant this permission with your permissions plugin.
- `svg.vc.group.type.isolated`: create isolated groups. Ops by default.
- `svg.vc.group.setpersistent`: create persistent groups.

---

## Group Persistence

* Persistent groups remain registered even when empty
* Non-persistent groups are automatically deleted when empty
