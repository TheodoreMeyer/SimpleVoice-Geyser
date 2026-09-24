Users connect via:

```text
https://yourdomain.com
```

Instead of:

http://<ip>:8080

## Summary
Setup Type-Status:

HTTP only-Not recommended
Chrome flags-Temporary workaround
HTTPS proxy-Correct solution

## Velocity: Built-in TLS

The Velocity proxy can terminate TLS directly by setting `ssl.type` to `file`.
It expects `ssl.file.cert` to be an X.509 PEM certificate and `ssl.file.key`
to be an unencrypted PKCS#8 PEM private key; paths are relative to the plugin
configuration directory. A reverse proxy remains recommended for certificate
chains and automatic renewal. See
[Velocity Proxy Setup]({% project_link velocity %}) for the full configuration
reference.
