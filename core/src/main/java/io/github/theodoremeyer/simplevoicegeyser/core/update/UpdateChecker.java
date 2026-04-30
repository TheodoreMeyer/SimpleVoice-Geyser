package io.github.theodoremeyer.simplevoicegeyser.core.update;

import io.github.theodoremeyer.simplevoicegeyser.core.api.chat.SvgLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Check to see if there is a new project update
 */
public final class UpdateChecker {

    /**
     * Modrinth ID
     */
    private static final String PROJECT_ID = "GJLuArlK";

    /**
     * Modrinth API URL
     */
    private static final String API_URL =
            "https://api.modrinth.com/v2/project/" + PROJECT_ID + "/version";

    /**
     * Modrinth Download URL
     */
    private static final String DOWNLOAD_URL =
            "https://modrinth.com/plugin/simplevoice-geyser";

    /**
     * Http Client for making API requests
     * @see HttpClient
     */
    private static final HttpClient HTTP = HttpClient.newHttpClient();

    private final SemanticVersion currentVersion;
    private final String serverMcVersion;
    private final SvgLogger logger;

    /**
     * Create an instance of the Update Checker
     * @param currentVersion project's current version
     * @param serverMcVersion minecraft version
     * @param logger logger to log to.
     */
    public UpdateChecker(String currentVersion,
                         String serverMcVersion,
                         SvgLogger logger) {
        this.currentVersion = SemanticVersion.parse(Objects.requireNonNull(currentVersion));
        this.serverMcVersion = Objects.requireNonNull(serverMcVersion);
        this.logger = Objects.requireNonNull(logger);
    }

    /**
     * Check for an update
     * @return something in the future
     */
    public CompletableFuture<Void> check() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("User-Agent", "SimpleVoiceGeyser-UpdateChecker")
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(this::findLatestCompatible)
                .thenAccept(this::handleResult)
                .exceptionally(this::handleFailure);
    }

    /**
     * Finds latest compatible version (by MC version + semantic version).
     */
    private VersionCandidate findLatestCompatible(String json) {
        JSONArray arr = new JSONArray(json);

        VersionCandidate best = null;

        for (int i = 0; i < arr.length(); i++) {
            JSONObject obj = arr.getJSONObject(i);

            String versionStr = obj.optString("version_number", null);
            String published = obj.optString("date_published", null);
            JSONArray mcVersions = obj.optJSONArray("game_versions");

            if (versionStr == null || published == null || mcVersions == null) {
                continue;
            }

            if (!supportsMcVersion(mcVersions, serverMcVersion)) {
                continue;
            }

            SemanticVersion version = SemanticVersion.parse(versionStr);
            Instant time = Instant.parse(published);

            VersionCandidate candidate = new VersionCandidate(versionStr, version, time);

            if (best == null || isBetter(candidate, best)) {
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Get the versions of mc that the version supports
     * @param arr JSON array
     * @param target target version
     * @return if it is compatible
     */
    private boolean supportsMcVersion(JSONArray arr, String target) {
        for (int i = 0; i < arr.length(); i++) {
            if (target.equals(arr.getString(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Priority:
     * 1. Higher semantic version wins
     * 2. If equal, newer publish date wins
     */
    private boolean isBetter(VersionCandidate a, VersionCandidate b) {
        int cmp = a.semantic.compareTo(b.semantic);
        if (cmp != 0) return cmp > 0;

        return a.published.isAfter(b.published);
    }

    private void handleResult(VersionCandidate latest) {
        if (latest == null) return;

        if (!latest.semantic.isNewerThan(currentVersion)) return;

        logger.warning("====================================================");
        logger.warning("Simple Voice Geyser update available");
        logger.warning("Current: " + currentVersion.major + "." + currentVersion.minor + "." + currentVersion.patch);
        logger.warning("Latest:  " + latest.versionRaw);
        logger.warning("Download: " + DOWNLOAD_URL);
        logger.warning("====================================================");
    }

    /**
     * Handle a failure
     * @param t throwable
     * @return void
     */
    private Void handleFailure(Throwable t) {
        logger.error("Update check failed", t);
        return null;
    }

    /**
     * A candidate for update versions
     * @param versionRaw raw version
     * @param semantic its semantic
     * @param published date published
     */
    private record VersionCandidate(
            String versionRaw,
            SemanticVersion semantic,
            Instant published
    ) {}
}