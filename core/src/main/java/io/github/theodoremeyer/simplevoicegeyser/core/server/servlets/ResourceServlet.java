package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Servlet responsible for serving static resources packaged inside the plugin JAR.
 */
public final class ResourceServlet extends HttpServlet {

    /**
     * Holds an instance of {@link FileManager} to manage resource extraction and access.
     */
    private final FileManager fileManager;

    /**
     * Create the servlet
     */
    public ResourceServlet() {
        this.fileManager = new FileManager();
        fileManager.extractAssets();
    }

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

        try (InputStream in = fileManager.getResourceStream(path)) {

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
