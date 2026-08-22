package io.github.theodoremeyer.simplevoicegeyser.velocity.proxy;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

public final class ResourceServlet extends HttpServlet {

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

            // Web assets and their websocket protocol change together on plugin updates.
            resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");

            in.transferTo(resp.getOutputStream());
        }
    }
}
