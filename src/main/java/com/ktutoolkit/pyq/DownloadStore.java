package com.ktutoolkit.pyq;

import com.ktutoolkit.settings.AppConfig;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Shared location for downloaded question papers, so the downloader and the
 * settings screen always agree on which folder to use and clear.
 */
public final class DownloadStore {
    private static final String FOLDER_NAME = "papers";

    private DownloadStore() {
    }

    /**
     * Returns the papers folder. The user can override it in Settings;
     * -Dktu.downloads is also accepted as a temporary override for testing.
     */
    public static Path root() {
        String override = System.getProperty("ktu.downloads");
        if (override == null || override.isBlank()) {
            override = new AppConfig().getPyqDownloadDirectory();
        }
        if (override == null || override.isBlank()) {
            return Paths.get(System.getProperty("user.home"), "Downloads", FOLDER_NAME);
        }
        return Paths.get(expandHome(override));
    }

    public static Path subjectDirectory(String subjectCode) {
        return root().resolve(subjectCode);
    }

    /**
     * Deletes every file and folder inside the papers folder.
     *
     * @return the number of files that were deleted
     */
    public static int deleteAll() throws IOException {
        Path root = root();
        if (!Files.exists(root)) {
            return 0;
        }

        int[] deletedFiles = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                deletedFiles[0]++;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        return deletedFiles[0];
    }

    private static String expandHome(String path) {
        if (path.equals("~") || path.startsWith("~/") || path.startsWith("~\\")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        return path;
    }
}
