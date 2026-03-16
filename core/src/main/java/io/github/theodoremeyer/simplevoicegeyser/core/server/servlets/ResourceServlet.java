package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Servlet responsible for serving static resources packaged inside the plugin JAR.
 */
public class ResourceServlet extends HttpServlet {

    /**
     * Root directory inside the JAR where static files are stored.
     */
    private static final String RESOURCE_ROOT = "/web";

    /**
     * Handles HTTP GET requests and serves the requested static resource.
     *
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
                mime = "application/octet-stream";
            }

            resp.setContentType(mime);

            // Optional caching header
            resp.setHeader("Cache-Control", "public, max-age=3600");

            // Stream file to client
            in.transferTo(resp.getOutputStream());
        }
    }
}