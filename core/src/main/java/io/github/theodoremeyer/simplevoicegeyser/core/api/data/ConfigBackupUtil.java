package io.github.theodoremeyer.simplevoicegeyser.core.api.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

/**
 * Writes config backups under {@code backups/configs/}, migrates legacy root
 * {@code config-*.bak} files, and rotates to a configurable max count.
 */
public final class ConfigBackupUtil {

    public static final String ENABLED_PATH = "config.backups.enabled";
    public static final String MAX_FILES_PATH = "config.backups.max-files";

    public static final boolean DEFAULT_ENABLED = true;
    public static final int DEFAULT_MAX_FILES = 6;

    private static final DateTimeFormatter BACKUP_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ConfigBackupUtil() {}

    /**
     * Backup {@code configFile} into {@code <dataFolder>/backups/configs/}.
     *
     * @param configFile live config file
     * @param settings   readable settings (may not yet contain backup keys → defaults)
     * @param extension  file extension without leading dot ({@code yml} / {@code json})
     * @return absolute path of the new backup, or empty if skipped / failed softly
     */
    public static String backup(File configFile, SvgFile settings, String extension) {
        if (configFile == null || !configFile.exists()) {
            return "";
        }

        boolean enabled = settings == null
                ? DEFAULT_ENABLED
                : settings.getBoolean(ENABLED_PATH, DEFAULT_ENABLED);
        if (!enabled) {
            return "";
        }

        int maxFiles = settings == null
                ? DEFAULT_MAX_FILES
                : settings.getInt(MAX_FILES_PATH, DEFAULT_MAX_FILES);
        if (maxFiles < 0) {
            maxFiles = DEFAULT_MAX_FILES;
        }

        File dataFolder = configFile.getParentFile();
        if (dataFolder == null) {
            return "";
        }

        File backupDir = new File(dataFolder, "backups" + File.separator + "configs");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            throw new RuntimeException("Failed to create config backup directory: " + backupDir);
        }

        String ext = normalizeExtension(extension);
        migrateLegacyRootBackups(dataFolder, backupDir, ext);

        String ts = LocalDateTime.now().format(BACKUP_TS);
        File dest = new File(backupDir, "config-" + ts + "." + ext + ".bak");
        try {
            Files.copy(configFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed backing up " + configFile.getName(), e);
        }

        if (maxFiles > 0) {
            rotate(backupDir, ext, maxFiles);
        }

        return dest.getAbsolutePath();
    }

    private static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return "yml";
        }
        String ext = extension.trim().toLowerCase(Locale.ROOT);
        if (ext.startsWith(".")) {
            ext = ext.substring(1);
        }
        return ext;
    }

    /**
     * Move old root-level {@code config-*.{ext}.bak} into {@code backups/configs/}.
     */
    private static void migrateLegacyRootBackups(File dataFolder, File backupDir, String ext) {
        File[] legacy = dataFolder.listFiles((dir, name) ->
                name.startsWith("config-")
                        && name.endsWith("." + ext + ".bak")
                        && !name.contains(File.separator)
        );
        if (legacy == null || legacy.length == 0) {
            return;
        }
        for (File file : legacy) {
            File target = new File(backupDir, file.getName());
            try {
                if (target.exists()) {
                    Files.deleteIfExists(file.toPath());
                } else {
                    Files.move(file.toPath(), target.toPath());
                }
            } catch (IOException ignored) {
                // best-effort cleanup
            }
        }
    }

    private static void rotate(File backupDir, String ext, int maxFiles) {
        File[] backups = backupDir.listFiles((dir, name) ->
                name.startsWith("config-") && name.endsWith("." + ext + ".bak")
        );
        if (backups == null || backups.length <= maxFiles) {
            return;
        }

        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));
        int toDelete = backups.length - maxFiles;
        for (int i = 0; i < toDelete; i++) {
            try {
                Files.deleteIfExists(backups[i].toPath());
            } catch (IOException ignored) {
                // best-effort
            }
        }
    }
}
