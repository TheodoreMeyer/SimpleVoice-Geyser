package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Servlet responsible for serving static resources packaged inside the plugin JAR.
 */
public final class ResourceServlet extends HttpServlet {

    /**
     * Create the servlet
     */
    public ResourceServlet() {}

    /**
     * Root directory inside the JAR where static files are stored.
     */
    private static final String RESOURCE_ROOT = "/web";

    /**
     * Handles HTTP GET requests and serves the requested static resource.
     * @param req  the HTTP request
     * @param resp the HTTP response
     * @throws IOException if the resource cannot be read or written
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String path = req.getPathInfo();

        // Root request → serve index.html
        if (path == null || path.equals("/")) {
            path = "/index.html";
        }

        // Prevent directory traversal attacks
        if (path.contains("..") || path.contains("\\")) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String resourcePath = RESOURCE_ROOT + path;

        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {

            // Resource not found
            if (in == null) {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            // Determine MIME type
            String mime = getServletContext().getMimeType(path);

            if (mime == null) {
                if (path.endsWith(".js") || path.endsWith(".mjs")) {
                    mime = "text/javascript";
                } else if (path.endsWith(".css")) {
                    mime = "text/css";
                } else if (path.endsWith(".html")) {
                    mime = "text/html; charset=UTF-8";
                } else if (path.endsWith(".json")) {
                    mime = "application/json";
                } else if (path.endsWith(".wasm")) {
                    mime = "application/wasm";
                } else if (path.endsWith(".svg")) {
                    mime = "image/svg+xml";
                } else if (path.endsWith(".png")) {
                    mime = "image/png";
                } else if (path.endsWith(".ico")) {
                    mime = "image/x-icon";
                } else {
                    mime = "application/octet-stream";
                }
            }

            resp.setContentType(mime);
            applySecurityHeaders(resp);
            applyCacheHeaders(path, req, resp);

            // Stream file to client
            in.transferTo(resp.getOutputStream());
        }
    }

    /**
     * Apply security headers compatible with microphone/WSS usage.
     */
    private static void applySecurityHeaders(HttpServletResponse resp) {
        resp.setHeader("X-Content-Type-Options", "nosniff");
        resp.setHeader("Referrer-Policy", "no-referrer");
        resp.setHeader("X-Frame-Options", "DENY");
        // frame-ancestors via CSP; allow same-origin scripts/styles/workers/media and WSS.
        // Opus decoder is self-hosted; wasm-unsafe-eval permits WebAssembly.compile without
        // broad unsafe-eval. Do not reintroduce CDN script/connect sources for voice.
        resp.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; "
                        + "script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; "
                        + "worker-src 'self' blob:; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "img-src 'self' data: https:; "
                        + "font-src 'self' data:; "
                        + "connect-src 'self' ws: wss:; "
                        + "media-src 'self' blob:; "
                        + "frame-ancestors 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'self'"
        );
    }

    /**
     * HTML is never cached. Fingerprinted assets ({@code ?v=buildId}) may be cached long.
     * Unversioned JS/CSS use {@code no-cache} so transitive ES module imports cannot stick
     * to a stale UI while {@code index.html} was already refreshed.
     */
    private static void applyCacheHeaders(String path, HttpServletRequest req, HttpServletResponse resp) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".html") || lower.equals("/index.html") || lower.equals("/")) {
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            resp.setHeader("Pragma", "no-cache");
            resp.setHeader("Expires", "0");
            return;
        }

        boolean versioned = req.getParameter("v") != null && !req.getParameter("v").isBlank();
        boolean staticAsset = lower.endsWith(".js")
                || lower.endsWith(".mjs")
                || lower.endsWith(".css")
                || lower.endsWith(".png")
                || lower.endsWith(".ico")
                || lower.endsWith(".svg")
                || lower.endsWith(".woff2");

        if (staticAsset && versioned) {
            resp.setHeader("Cache-Control", "public, max-age=31536000, immutable");
            return;
        }

        if (staticAsset) {
            // Critical for ES module graphs: child imports often omit ?v=.
            resp.setHeader("Cache-Control", "no-cache");
            return;
        }

        resp.setHeader("Cache-Control", "no-cache");
    }
}
