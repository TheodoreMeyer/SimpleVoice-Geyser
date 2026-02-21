package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Class that makes the websocket client's mic work
 * Returns the js file.
 */
public class MicWorkletServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/javascript");
        try (var in = getClass().getResourceAsStream("/web/js/mic-capture-processor.js")) {
            if (in == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            in.transferTo(resp.getOutputStream());
        }
    }
}
