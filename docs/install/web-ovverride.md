---
title: Web Override
layout: projects
project: simplevoicegeyser
---

# Web Asset Overrides

Simple Voice Geyser includes web assets inside the plugin JAR, such as:

* `index.html`
* `css/styles.css`

These files are extracted to:

```text
plugins/SimpleVoiceGeyser/web/
```

The files in this directory are modifiable to customize the web interface.

## Automatic Updates

A `.versioning` file is used to decide if a local web file has been modified.

Example:

```text
plugins/
└── SimpleVoiceGeyser/
    └── web/
        ├── index.html
        ├── css/
        │   └── styles.css
        └── .versioning
```

The `.versioning` file stores the SHA-256 hash of the last bundled version of each web file.

Note: It is not recommended to edit the .versioning file, it can mess up the updating process.

For example:

```properties
index.html=...
css/styles.css=...
```

The stored hash represents the version of the file that was last provided by the plugin JAR.

## When a File Is Not Modified

Suppose version `0.1.3` contains:

```text
index.html = Version A
```

After installation:

```text
Local index.html = Version A
.versioning = hash(Version A)
```

Version `0.1.4` contains:

```text
index.html = Version B
```

Since the local file still matches the hash stored in `.versioning`, Svg knows that the file was not modified locally.

The file is automatically updated:

```text
Version A → Version B
```

The `.versioning` file is then updated to contain:

```text
hash(Version B)
```

## When a File Has Been Modified

Suppose version `0.1.3` contains:

```text
index.html = Version A
```

The user modifies the local file:

```text
Local index.html = User Modified Version
.versioning = hash(Version A)
```

Version `0.1.4` contains a new version:

```text
JAR index.html = Version B
```

Because the local file doesn't match the hash stored in `.versioning`, Svg decides the file has been modified locally.

The local file is preserved and is **not overwritten**.

A warning is printed to the console:

```text
The following web files have been locally modified and will not be
automatically updated during plugin upgrades.
```

If the bundled version has also changed, the file is additionally reported as being based on an older bundled version. This means that the locally modified file may not contain changes introduced in the current plugin release.

## Restoring the Bundled Version

To restore the current version of a web file bundled with Simple Voice Geyser:

1. Stop the server.
2. Delete the modified local web file.
3. Start the server again.

For example:

```text
plugins/SimpleVoiceGeyser/web/index.html
```

After the file is deleted, Simple Voice Geyser extracts the current version from the plugin JAR during startup.

## Summary

| Local file state    | Plugin bundled file changed | Result                                     |
| ------------------- | --------------------------- | ------------------------------------------ |
| File does not exist | Any                         | Extract bundled file                       |
| File unchanged      | No                          | Keep existing file                         |
| File unchanged      | Yes                         | Automatically update file                  |
| File modified       | No                          | Preserve local file                        |
| File modified       | Yes                         | Preserve file and warn that it is outdated |

The system never automatically overwrites a locally modified web file.
