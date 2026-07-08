---
title: Android Awareness
layout: projects
project: simplevoicegeyser
---

# Android Awareness

Simple Voice Geyser 0.1.3 adds client awareness for websocket connections so the built-in Web client and the native Svg-App can identify themselves before authentication.

This lets the server keep browser build protection, accept the Android app without requiring a browser build id, and optionally allow or block client families from config.

## Client Types

Supported client type names are:

| Client type | Purpose |
|-------------|---------|
| `Web` | Built-in browser client served by the plugin. |
| `Svg-App` | Native Android app client. |

The client type is sent in the `join` packet as `clientType`.

### Web

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "clientType": {
    "type": "Web",
    "serverVersion": "<SvgCore.VERSION>",
    "serverBuild": "<SvgCore.BUILD_ID>"
  }
}
```

`Web` must include `serverBuild`. The server validates it against `SvgCore.BUILD_ID` so stale browser assets are rejected.

For 0.1.x compatibility, old browser joins with top-level `build` are still accepted. This path is marked in code for removal when the next compatibility break removes the legacy browser join shape.

### Svg-App

```json
{
  "type": "join",
  "username": "PlayerName",
  "password": "password-value",
  "clientType": {
    "type": "Svg-App",
    "serverVersion": "<SvgCore.VERSION>"
  }
}
```

`Svg-App` does not send `build` or `serverBuild`. It targets the plugin project version through `serverVersion`.

## Validation Order

Client compatibility is checked before username/password authentication.

| Case | Close result |
|------|--------------|
| Malformed `clientType` | `4005` / `invalid_client_info` |
| Unknown or config-blocked client type | `4005` / `unsupported_client_type` |
| Missing or stale Web build | `4008` / `update_required` |
| Svg-App server version mismatch | `4008` / `app_protocol_unsupported` |

Old Android metadata using `client.kind`, `client.version`, or `client.protocol` is not accepted by the 0.1.3 client awareness model.

## Config

Server owners can allow or block client families with:

```yaml
client:
  allowedTypes:
    isBlackList: true
    list:
      # - Web
      # - Svg-App
```

When `isBlackList` is `true`, listed types are blocked. When it is `false`, only listed types are allowed. The default list is empty, so no client type is blocked by default.

## Capabilities And Audio

After join and authentication succeed, clients still send the normal `capabilities` packet. Client awareness does not replace audio capability negotiation.

The Android app should keep advertising legacy audio support until its `svg-v2` receive/playback path is fully implemented and tested. The server can still negotiate `legacy` or `svg-v2` per session.

Voice routing remains managed by Simple Voice Chat through the per-player audio listener path. The Android-awareness PR does not use a global microphone-packet fan-out.

## Testing Checklist

- Web client joins with matching `clientType.serverBuild`.
- Legacy Web join with top-level `build` still works during 0.1.x.
- Svg-App joins with `clientType.type = "Svg-App"` and matching `serverVersion`.
- Wrong password still fails after compatibility validation.
- Blacklist/whitelist config accepts or rejects `Web` and `Svg-App` as expected.
- Chat still works after authentication.
- Bedrock/App to Java voice and Java to Bedrock/App voice both work.
