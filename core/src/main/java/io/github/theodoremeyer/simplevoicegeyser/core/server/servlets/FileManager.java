package io.github.theodoremeyer.simplevoicegeyser.core.server.servlets;

import io.github.theodoremeyer.simplevoicegeyser.core.SvgCore;

import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;

class FileManager {

    /**
     * Root directory inside the JAR where static files are stored.
     */
    protected static final String RESOURCE_ROOT = "/web";

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
     */
    void extractAssets() {

        // Clear these in case extractAssets() is ever called more than once.
        modifiedFiles.clear();
        outdatedFiles.clear();

        extractResource(
                "/web/index.html",
                "index.html"
        );

        extractResource(
                "/web/css/styles.css",
                "css/styles.css"
        );

        extractResource(
                "/web/v2.html",
                "v2.html"
        );

        extractResource(
                "/web/css/v2.css",
                "css/v2.css"
        );

        extractResource(
                "/web/images/v2-background.png",
                "images/v2-background.png"
        );
        extractResource(
                "/web/images/docs.png",
                "images/docs.png"
        );
        extractResource(
                "/web/images/github.png",
                "images/github.png"
        );
        extractResource(
                "/web/images/modrinth.png",
                "images/modrinth.png"
        );
        extractResource(
                "/web/images/link.png",
                "images/link.png"
        );

        extractResource(
                "/web/images/flags-1x1/en.svg",
                "images/flags-1x1/en.svg"
        );
        extractResource(
                "/web/images/flags-1x1/pl.svg",
                "images/flags-1x1/pl.svg"
        );
        extractResource(
                "/web/images/flags-1x1/it.svg",
                "images/flags-1x1/it.svg"
        );

        versioning.save();

        logModifiedFiles();

        SvgCore.getLogger().info(
                "Loaded Override web files from "
                        + webDirectory.getAbsolutePath()
        );
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

        String localPath = path.startsWith("/") ? path.substring(1) : path;

        File localFile = new File(webDirectory, localPath);

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
         */
        String jarResourcePath = RESOURCE_ROOT + "/" + localPath;

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