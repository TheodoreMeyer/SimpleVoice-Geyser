package io.github.theodoremeyer.spigotmc.simplevoicegeyser.server.servlets;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public class ClientWorkletServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/javascript");
        try (var in = getClass().getResourceAsStream("/web/js/client.js")) {
            if (in == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            in.transferTo(resp.getOutputStream());
        }
    }
}
