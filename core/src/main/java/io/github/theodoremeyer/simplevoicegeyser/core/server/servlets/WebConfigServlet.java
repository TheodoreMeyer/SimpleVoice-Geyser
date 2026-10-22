package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Exposes public web branding / default language settings to the browser UI.
 */
public final class WebConfigServlet extends HttpServlet {

    public WebConfigServlet() {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String defaultLanguage = SvgCore.getConfig().WEB_DEFAULT_LANGUAGE.get();
        if (defaultLanguage == null || defaultLanguage.isBlank()) {
            defaultLanguage = "en";
        } else {
            defaultLanguage = defaultLanguage.trim().toLowerCase();
        }

        String serverName = SvgCore.getConfig().WEB_SERVER_NAME.get();
        if (serverName == null || serverName.isBlank()) {
            serverName = "Simple Voice Chat";
        }

        String tagline = SvgCore.getConfig().WEB_TAGLINE.get();
        if (tagline == null) {
            tagline = "";
        }

        int joinTimeoutSeconds = Math.max(1, SvgCore.getConfig().VC_TIMEOUT.get());

        JSONObject json = new JSONObject();
        json.put("defaultLanguage", defaultLanguage);
        json.put("serverName", serverName.trim());
        json.put("tagline", tagline.trim());
        json.put("joinTimeoutSeconds", joinTimeoutSeconds);

        byte[] body = json.toString().getBytes(StandardCharsets.UTF_8);
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resp.setContentType("application/json; charset=UTF-8");
        resp.setHeader("Cache-Control", "no-cache");
        resp.setContentLength(body.length);
        resp.getOutputStream().write(body);
    }
}
