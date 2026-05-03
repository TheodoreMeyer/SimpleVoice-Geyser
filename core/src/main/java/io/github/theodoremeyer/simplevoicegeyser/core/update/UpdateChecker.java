package io.github.theodoremeyer.simplevoicegeyser.core.update;

import io.github.theodoremeyer.simplevoicegeyser.core.api.Platform;
import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Checks Modrinth for newer compatible versions.
 */
public final class UpdateChecker {

    private static final String PROJECT_ID = "GJLuArlK";
    private static final String DOWNLOAD_URL =
            "https://modrinth.com/plugin/simplevoice-geyser";

    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final SemanticVersion currentVersion;
    private final String serverMcVersion;
    private final Platform platform;
    private final SvgLogger logger;

    public UpdateChecker(String currentVersion, Platform platform) {

        this.currentVersion = SemanticVersion.parse(
                Objects.requireNonNull(currentVersion, "currentVersion")
        );

        this.platform = Objects.requireNonNull(platform, "platform");

        this.serverMcVersion = platform.getServerMcVersion();
        this.logger = platform.getSvgLogger();
    }

    /**
     * Performs an asynchronous update check.
     */
    public CompletableFuture<Void> check() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(buildApiUrl()))
                .header("User-Agent", "SimpleVoiceGeyser-UpdateChecker")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(this::findLatestVersion)
                .thenAccept(this::handleResult)
                .exceptionally(this::handleFailure);
    }

    /**
     * Builds a Modrinth API URL filtered by loader and Minecraft version.
     */
    private String buildApiUrl() {
        String loaders = "[\"" + getLoaderName() + "\"]";
        String versions = "[\"" + serverMcVersion + "\"]";

        return "https://api.modrinth.com/v2/project/"
                + PROJECT_ID
                + "/version?loaders="
                + URLEncoder.encode(loaders, StandardCharsets.UTF_8)
                + "&game_versions="
                + URLEncoder.encode(versions, StandardCharsets.UTF_8);
    }

    /**
     * Maps platform to Modrinth loader name.
     */
    private String getLoaderName() {
        return platform.getServerPlatform().toLowerCase();
    }

    /**
     * Finds the newest version returned by Modrinth.
     */
    private VersionCandidate findLatestVersion(String json) {
        JSONArray versions = new JSONArray(json);

        VersionCandidate best = null;

        for (int i = 0; i < versions.length(); i++) {
            JSONObject obj = versions.getJSONObject(i);

            String versionRaw = obj.optString("version_number", null);
            String publishedRaw = obj.optString("date_published", null);

            if (versionRaw == null || publishedRaw == null) {
                continue;
            }

            SemanticVersion semantic;
            try {
                semantic = SemanticVersion.parse(versionRaw);
            } catch (Exception ignored) {
                continue;
            }

            Instant published;
            try {
                published = Instant.parse(publishedRaw);
            } catch (Exception ignored) {
                continue;
            }

            VersionCandidate candidate =
                    new VersionCandidate(versionRaw, semantic, published);

            if (best == null || isBetter(candidate, best)) {
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Chooses the better candidate.
     */
    private boolean isBetter(VersionCandidate a, VersionCandidate b) {
        int comparison = a.semantic.compareTo(b.semantic);

        if (comparison != 0) {
            return comparison > 0;
        }

        return a.published.isAfter(b.published);
    }

    /**
     * Handles a successful update check.
     */
    private void handleResult(VersionCandidate latest) {
        if (latest == null) {
            return;
        }

        if (!latest.semantic.isNewerThan(currentVersion)) {
            return;
        }

        logger.warning("====================================================");
        logger.warning("A new version of SimpleVoice-Geyser is available!");
        logger.warning("Current Version: " + currentVersion);
        logger.warning("Latest Version:  " + latest.versionRaw);
        logger.warning("Download Here:   " + DOWNLOAD_URL);
        logger.warning("====================================================");
    }

    /**
     * Handles failures gracefully.
     */
    private Void handleFailure(Throwable throwable) {
        logger.error("Failed to check for updates", throwable);
        return null;
    }

    /**
     * Represents a Modrinth version candidate.
     */
    private record VersionCandidate(
            String versionRaw,
            SemanticVersion semantic,
            Instant published
    ) {
    }
}