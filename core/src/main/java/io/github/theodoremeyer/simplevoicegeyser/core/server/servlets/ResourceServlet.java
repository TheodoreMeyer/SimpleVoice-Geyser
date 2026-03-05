package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Servlet that serves static resources from the jar.
 */
public class ResourceServlet extends HttpServlet {

    private final String resourcePath;
    private final String contentType;

    public ResourceServlet(String resourcePath, String contentType) {
        this.resourcePath = resourcePath;
        this.contentType = contentType;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        resp.setContentType(contentType);

        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {

            if (in == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }

            in.transferTo(resp.getOutputStream());
        }
    }
}