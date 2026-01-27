package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Needs to be worked on. Major bug with sound.
 * Partially Complete now!
 * HTML server page
 */
public class JettyHtmlServlet extends HttpServlet {
    /**
     * HTML Page
     * Needs to be worked on.
     * @param req request
     * @param resp http-response
     * @throws IOException Exception
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html");
        try (var in = getClass().getResourceAsStream("/web/index.html")) {
            if (in == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            in.transferTo(resp.getOutputStream());
        }
    }
}
