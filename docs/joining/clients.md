---
title: Who Can Join
layout: projects
project: simplevoicegeyser
---

# Who Can Join: Web Page, App, or Both

Players can reach your voice chat in two ways: the web page in a browser, and the [Svg-App]({% project_link app %}) on Android. By default both are allowed. This page shows how to allow only one of them, or neither.

## What to change

Everything is in one setting in the server's Simple Voice Geyser config. Two lines decide the outcome:

- `list` holds client names. There are two: `Web` (the browser page) and `Svg-App` (the Android app). Capital letters do not matter.
- `isBlackList` decides what the list means.
  - `true`: the clients in the list are **blocked**, everyone else is allowed. This is the default, with an empty list.
  - `false`: only the clients in the list are **allowed**, everyone else is blocked.

Spigot, Paper and Purpur use `plugins/SimpleVoice-Geyser/config.yml`:

```yaml
client:
  allowedTypes:
    isBlackList: true
    list: []
```

Fabric uses `config/SimpleVoice-Geyser/config.json`:

```json
{
  "client": {
    "allowedTypes": {
      "isBlackList": true,
      "list": []
    }
  }
}
```

## Pick what you want

**Allow both (the default)**

```yaml
client:
  allowedTypes:
    isBlackList: true
    list: []
```

**Browser only: block the app**

```yaml
client:
  allowedTypes:
    isBlackList: true
    list:
      - Svg-App
```

**App only: block the browser**

```yaml
client:
  allowedTypes:
    isBlackList: true
    list:
      - Web
```

**Nobody: block both**

```yaml
client:
  allowedTypes:
    isBlackList: true
    list:
      - Web
      - Svg-App
```

The same results can be written the other way round with `isBlackList: false` and the allowed names in the list. For example `isBlackList: false` with only `Svg-App` listed means app only.

## Apply the change

Save the file and run `/svg reload` (admin permission `svg.admin`, or the console). The new rule is used the next time somebody joins. Players who are already connected stay connected until they leave.

## What the player sees

- **Browser:** the page opens normally, but signing in fails with "Unsupported client type."
- **App:** it shows "This server does not allow the Svg-App client." and does not keep retrying.

## Things to know

- Separate from this setting, every player needs the permission `svg.vc.join` to join voice chat. Players have it by default.
- This blocks the sign-in. It does not hide the web page. If you want the page itself to be unreachable, do not open or forward the web port.
- Only `Web` and `Svg-App` exist. Anything else that tries to join is refused.
- Blocking the app does not affect Java players who use the Simple Voice Chat mod. They are not using this.
- **Velocity networks:** the rule is read by each backend, so set it in every backend's config. The proxy serves the page and the connection point, but it does not apply this rule.

## Check that it works

1. Set the rule and run `/svg reload`.
2. Try to sign in with the client you blocked. It should be refused with the message above.
3. Try the client you allowed. It should connect as normal.
