package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

class FileManager {

    /**
     * Root directory inside the JAR where static files are stored.
     */
    protected static final String RESOURCE_ROOT = "/web";

    /**
     * Entry HTML embeds {@code BUILD_ID}; always overwrite from the JAR so joins
     * do not fail with {@code update_required} against a stale disk copy.
     */
    private static final Set<String> ALWAYS_OVERWRITE = Set.of(
            "index.html",
            "legacy.html"
    );

    /**
     * Local directory where web files are stored.
     */
    private final File webDirectory =
            new File(
                    SvgCore.getPlatform().getDataFolder(),
                    "web"
            );

    /**
     * Tracks the hash of the last bundled version of each web file.
     */
    private final Versioning versioning;

    /**
     * Files that have been modified locally.
     */
    private final List<String> modifiedFiles =
            new ArrayList<>();

    /**
     * Modified files for which the bundled JAR version has changed.
     *
     * <p>These files are locally modified and are therefore not updated,
     * but they may be missing changes included in the current plugin release.</p>
     */
    private final List<String> outdatedFiles =
            new ArrayList<>();

    FileManager() {

        if (!webDirectory.exists() && !webDirectory.mkdirs()) {
            SvgCore.getLogger().error(
                    "Failed to create web directory: "
                            + webDirectory.getAbsolutePath()
            );
        }

        this.versioning =
                new Versioning(webDirectory);
    }

    /**
     * Automatically extracts required web assets from the JAR
     * to the local file system on startup.
     *
     * <p>Extracts the full {@code /web/} tree (HTML/CSS/JS/images) so ES-module
     * imports like {@code js/utils/lang.js} are always present on disk — not only
     * a partial whitelist. Locally modified files are still preserved via versioning.</p>
     */
    void extractAssets() {

        // Clear these in case extractAssets() is ever called more than once.
        modifiedFiles.clear();
        outdatedFiles.clear();

        int extracted = extractAllWebResources();
        if (extracted == 0) {
            SvgCore.getLogger().error(
                    "[Web] Failed to discover bundled /web assets; UI modules may 404."
            );
        } else {
            SvgCore.getLogger().info(
                    "[Web] Processed " + extracted + " bundled web asset(s)."
            );
        }

        versioning.save();

        logModifiedFiles();

        SvgCore.getLogger().info(
                "Loaded Override web files from "
                        + webDirectory.getAbsolutePath()
        );
    }

    /**
     * Discovers every regular file under {@code /web/} in the plugin JAR (or
     * exploded classpath) and runs {@link #extractResource(String, String)}.
     *
     * @return number of resources processed
     */
    private int extractAllWebResources() {
        int fromJar = extractWebResourcesFromPluginJar();
        if (fromJar > 0) {
            return fromJar;
        }
        return extractWebResourcesFromClasspathRoot();
    }

    /**
     * Walk the plugin JAR file for entries under {@code web/}.
     */
    private int extractWebResourcesFromPluginJar() {
        CodeSource codeSource = FileManager.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            return 0;
        }

        try {
            URI location = codeSource.getLocation().toURI();
            File jarFile = new File(location);
            if (!jarFile.isFile()) {
                return 0;
            }

            int count = 0;
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    String name = entry.getName();
                    if (!name.startsWith("web/") || name.equals("web/")) {
                        continue;
                    }
                    // Skip internal versioning file if ever packaged
                    if (name.endsWith("/.versioning") || name.equals("web/.versioning")) {
                        continue;
                    }
                    String relative = name.substring("web/".length());
                    if (relative.isEmpty()) {
                        continue;
                    }
                    extractResource("/" + name, relative);
                    count++;
                }
            }
            return count;
        } catch (URISyntaxException | IOException e) {
            SvgCore.getLogger().warning(
                    "[Web] Could not scan plugin JAR for web assets: " + e.getMessage()
            );
            return 0;
        }
    }

    /**
     * Fallback when running from an exploded classpath (e.g. IDE).
     */
    private int extractWebResourcesFromClasspathRoot() {
        URL rootUrl = FileManager.class.getResource(RESOURCE_ROOT);
        if (rootUrl == null) {
            return 0;
        }

        try {
            URI uri = rootUrl.toURI();
            if ("jar".equalsIgnoreCase(uri.getScheme())) {
                return extractWebResourcesFromJarUri(uri);
            }

            Path root = Path.of(uri);
            int count = 0;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    if (relative.isEmpty() || relative.endsWith(".versioning")) {
                        continue;
                    }
                    extractResource(RESOURCE_ROOT + "/" + relative, relative);
                    count++;
                }
            }
            return count;
        } catch (URISyntaxException | IOException e) {
            SvgCore.getLogger().warning(
                    "[Web] Could not walk classpath /web tree: " + e.getMessage()
            );
            return 0;
        }
    }

    private int extractWebResourcesFromJarUri(URI uri) throws IOException {
        FileSystem fs = null;
        boolean created = false;
        try {
            try {
                fs = FileSystems.getFileSystem(uri);
            } catch (Exception ignored) {
                fs = FileSystems.newFileSystem(uri, Collections.emptyMap());
                created = true;
            }

            Path root = fs.getPath(RESOURCE_ROOT);
            if (!Files.exists(root)) {
                return 0;
            }

            int count = 0;
            try (Stream<Path> walk = Files.walk(root)) {
                for (Path path : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                    String relative = root.relativize(path).toString().replace('\\', '/');
                    if (relative.isEmpty() || relative.endsWith(".versioning")) {
                        continue;
                    }
                    extractResource(RESOURCE_ROOT + "/" + relative, relative);
                    count++;
                }
            }
            return count;
        } finally {
            if (created && fs != null) {
                fs.close();
            }
        }
    }

    /**
     * Extracts a single resource from inside the JAR.
     *
     * <p>Files are handled as follows:</p>
     *
     * <ul>
     *     <li>
     *         A missing local file is created from the bundled resource.
     *     </li>
     *
     *     <li>
     *         An unchanged local file is automatically updated when the
     *         bundled resource changes.
     *     </li>
     *
     *     <li>
     *         A locally modified file is preserved.
     *     </li>
     * </ul>
     *
     * @param resourcePath path to the resource inside the JAR
     * @param outputPath path relative to the local web directory
     */
    private void extractResource(String resourcePath, String outputPath) {

        File targetFile = new File(webDirectory, outputPath);

        try (InputStream input = FileManager.class.getResourceAsStream(resourcePath)) {

            if (input == null) {
                SvgCore.getLogger().warning(
                        "Resource not found inside JAR: "
                                + resourcePath
                );

                return;
            }

            /*
             * Read the bundled file once.
             *
             * These bytes are used both for hashing and, if necessary,
             * writing the file to disk.
             */
            byte[] bundledData = input.readAllBytes();

            String bundledHash = Versioning.hash(bundledData);

            /*
             * Entry HTML carries the jar BUILD_ID used for websocket auth.
             * Always refresh from the JAR so upgrades cannot leave a stale
             * build id (which causes an endless "Update required" reload loop).
             */
            if (ALWAYS_OVERWRITE.contains(outputPath)) {
                writeFile(targetFile, bundledData);
                versioning.setHash(outputPath, bundledHash);
                return;
            }

            /*
             * The local file does not exist.
             *
             * Install the bundled version.
             */
            if (!targetFile.exists()) {

                writeFile(targetFile, bundledData);

                versioning.setHash(outputPath, bundledHash);

                return;
            }

            /*
             * Get the hash of the bundled version that was previously
             * installed locally.
             */
            String previousBundledHash =
                    versioning.getHash(outputPath);

            /*
             * If there is no previous hash, this file predates the
             * versioning system.
             *
             * We cannot safely determine whether the user modified it,
             * so preserve the existing file.
             */
            if (previousBundledHash == null) {

                modifiedFiles.add(outputPath);

                if (!bundledHash.equals(hashFile(targetFile))) {
                    outdatedFiles.add(outputPath);
                }

                return;
            }

            /*
             * Calculate the hash of the current local file.
             */
            String currentLocalHash = hashFile(targetFile);

            /*
             * The local file still matches the previous bundled version.
             *
             * The user has not modified it.
             */
            if (currentLocalHash.equals(previousBundledHash)) {

                /*
                 * The file is unchanged locally.
                 *
                 * Replace it with the current bundled version.
                 */
                if (!bundledHash.equals(previousBundledHash)) {

                    writeFile(targetFile, bundledData);
                }

                /*
                 * Always update the stored bundled hash.
                 *
                 * This is important even if the new bundled file is
                 * identical to the old bundled file.
                 */
                versioning.setHash(outputPath, bundledHash);

                return;
            }

            /*
             * The local file differs from the previously bundled version.
             *
             * The user has modified it.
             */
            modifiedFiles.add(outputPath);

            /*
             * The bundled version has also changed.
             *
             * Therefore, the user's local file is now based on an older
             * bundled version and may be missing changes from this release.
             */
            if (!bundledHash.equals(previousBundledHash)) {

                outdatedFiles.add(outputPath);
            }

            /*
             * Do not update the file.
             */
        } catch (IOException e) {

            SvgCore.getLogger().error("Failed to extract resource: " + resourcePath, e);
        }
    }

    /**
     * Logs information about modified local web files.
     */
    private void logModifiedFiles() {

        if (modifiedFiles.isEmpty()) {
            return;
        }

        SvgCore.getLogger().warning(
                "[Web] The following web files have been locally "
                        + "modified and will not be automatically updated "
                        + "during plugin upgrades:"
        );

        for (String file : modifiedFiles) {
            SvgCore.getLogger().warning(
                    "[Web]   - " + file
            );
        }

        if (!outdatedFiles.isEmpty()) {

            SvgCore.getLogger().warning(
                    "[Web] The following modified web files are based "
                            + "on an older bundled version and may not "
                            + "contain changes from the current release:"
            );

            for (String file : outdatedFiles) {
                SvgCore.getLogger().warning("[Web]   - " + file);
            }
        }

        SvgCore.getLogger().warning(
                "[Web] To restore the current bundled version of a file, "
                        + "delete the local file and restart the server."
        );
    }

    /**
     * Calculates the SHA-256 hash of a local file.
     */
    private String hashFile(File file) throws IOException {

        return Versioning.hash(Files.readAllBytes(file.toPath()));
    }

    /**
     * Writes data to a local file.
     */
    private void writeFile(File targetFile, byte[] data) throws IOException {

        File parentDirectory = targetFile.getParentFile();

        if (parentDirectory != null
                && !parentDirectory.exists()
                && !parentDirectory.mkdirs()
                && !parentDirectory.exists()
        ) {
            throw new IOException("Failed to create directory: " + parentDirectory.getAbsolutePath());
        }

        try (FileOutputStream output = new FileOutputStream(targetFile)) {
            output.write(data);
        }
    }

    /**
     * Returns an InputStream for the requested resource path.
     *
     * <p>The local web directory is checked first. This allows local
     * files to override resources bundled inside the JAR.</p>
     *
     * @param path relative web path
     * @return resource stream, or null if not found
     */
    InputStream getResourceStream(String path) {
        // 1. Normalize the base directory to an absolute path
        java.nio.file.Path base = webDirectory.toPath().toAbsolutePath().normalize();

        // 2. Strip leading slash to prevent path resolution from discarding the base directory
        String cleanedPath = path.startsWith("/") ? path.substring(1) : path;

        // 3. Resolve and normalize the requested file path
        java.nio.file.Path resolved = base.resolve(cleanedPath).toAbsolutePath().normalize();

        // 4. Defense-in-depth: Ensure the resolved path remains inside the base directory
        if (!resolved.startsWith(base)) {
            return null;
        }

        // 5. Convert back to a File object
        File localFile = resolved.toFile();

        /*
         * Local override takes priority.
         */
        if (localFile.exists() && localFile.isFile()) {
            try {
                return new FileInputStream(localFile);
            } catch (FileNotFoundException e) {
                // File may have disappeared between exists() and opening it.
            }
        }

        /*
         * Fall back to the resource bundled inside the JAR.
         * We reconstruct a clean, normalized relative path for the JAR resource.
         */
        String cleanRelativePath = base.relativize(resolved).toString().replace(File.separatorChar, '/');
        String jarResourcePath = RESOURCE_ROOT + "/" + cleanRelativePath;

        return FileManager.class.getResourceAsStream(jarResourcePath);
    }

    /**
     * Tracks the hash of the last bundled version of each web file.
     */
    private static final class Versioning {

        private static final String FILE_NAME = ".versioning";

        private final File file;

        private final Properties hashes = new Properties();

        private Versioning(File webDirectory) {

            this.file = new File(webDirectory, FILE_NAME);

            load();
        }

        /**
         * Gets the hash of the last bundled version of a file.
         */
        private String getHash(String path) {

            return hashes.getProperty(path);
        }

        /**
         * Stores the hash of the currently installed bundled version.
         */
        private void setHash(String path, String hash) {

            hashes.setProperty(path, hash);
        }

        /**
         * Loads hashes from the .versioning file.
         */
        private void load() {

            if (!file.exists()) {
                return;
            }

            try (FileInputStream input = new FileInputStream(file)) {

                hashes.load(input);

            } catch (IOException e) {

                SvgCore.getLogger().error(
                        "Failed to load web file versioning: ",
                        e
                );
            }
        }

        /**
         * Saves hashes to the .versioning file.
         */
        private void save() {

            try (FileOutputStream output = new FileOutputStream(file)) {

                hashes.store(output, "Simple Voice Geyser web asset versioning");
            } catch (IOException e) {
                SvgCore.getLogger().error(
                        "Failed to save web file versioning: ",
                        e
                );
            }
        }

        /**
         * Calculates an SHA-256 hash for the supplied data.
         */
        private static String hash(byte[] data) throws IOException {

            try {

                MessageDigest digest = MessageDigest.getInstance("SHA-256");

                return HexFormat.of().formatHex(digest.digest(data));

            } catch (NoSuchAlgorithmException e) {

                throw new IOException(
                        "SHA-256 algorithm is unavailable",
                        e
                );
            }
        }
    }
}