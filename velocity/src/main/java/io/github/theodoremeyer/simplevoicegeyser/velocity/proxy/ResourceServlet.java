package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Serves the bundled web client (resources under {@code /web}) and rejects
 * path traversal attempts. Responses are sent with no-store caching so the
 * client build always matches the plugin.
 */
public final class ResourceServlet extends HttpServlet {

    /** Default constructor required by the servlet container. */
    public ResourceServlet() {}

    private static final String RESOURCE_ROOT = "/web";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String path = req.getPathInfo();

        if (path == null || path.equals("/")) {
            path = "/index.html";
        }

        if (path.contains("..") || path.contains("\\")) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String resourcePath = RESOURCE_ROOT + path;

        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {

            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            String mime = getServletContext().getMimeType(path);

            if (mime == null) {
                mime = "application/octet-stream";
            }

            resp.setContentType(mime);

            byte[] content = in.readAllBytes();
            serveWithValidation(req, resp, path, content);
        }
    }

    private static void serveWithValidation(HttpServletRequest req, HttpServletResponse resp,
                                            String path, byte[] content) throws IOException {
        if (path.endsWith(".html") || path.endsWith("manifest.json")) {
            resp.setHeader("Cache-Control", "no-cache");
        } else {
            try {
                String etag = '\"' + java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)) + '\"';
                resp.setHeader("Cache-Control", "no-cache");
                resp.setHeader("ETag", etag);
                if (etag.equals(req.getHeader("If-None-Match"))) {
                    resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    return;
                }
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException("SHA-256 is unavailable", e);
            }
        }
        resp.getOutputStream().write(content);
    }
}
