# Flutter Android App Handoff: `clientType` Join Update

Update the app websocket join packet to match the plugin's generic client awareness model. Replace the old Android-specific metadata:

```json
"client": {
  "kind": "android",
  "version": "1.0.0",
  "protocol": 1
}
```

with:

```json
"clientType": {
  "type": "Svg-App",
  "serverVersion": "0.1.3"
}
```

Keep the rest of the join packet unchanged: `type: "join"`, `username`, and `password`. Do not include the browser `build` or `serverBuild` fields for the app.

After the connected/authenticated status response, keep sending the existing `capabilities` packet and keep handling `capabilities_ack`. Until the app has a verified `svg-v2` Opus decode/playback path, keep advertising conservative legacy receive support rather than making the app `svg-v2` only.

Test against the rebuilt plugin jar:

- valid Svg-App join succeeds;
- wrong password still fails through the normal auth path;
- old `client.kind/version/protocol` metadata is rejected with `4005 / invalid_client_info`;
- mismatched `clientType.serverVersion` is rejected with `4008 / app_protocol_unsupported`;
- capabilities and `capabilities_ack` still work after auth;
- chat still works;
- current audio behavior is unchanged.
