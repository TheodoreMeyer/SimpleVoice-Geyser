---
layout: projects
title: Commands
permalink: /projects/simplevoicegeyser/commands/
sidebar: simplevoicegeyser
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
* Used for authentication on the web client

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

## Group Persistence

* Persistent groups remain registered even when empty
* Non-persistent groups are automatically deleted when empty
