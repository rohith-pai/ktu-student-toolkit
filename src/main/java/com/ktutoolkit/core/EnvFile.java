package com.ktutoolkit.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads a simple KEY=VALUE .env file into JVM system properties.
 *
 * <p>No third-party library is needed. Real environment variables still take
 * precedence, and an explicit -Dproperty=value also wins over the file.</p>
 */
public final class EnvFile {
    private EnvFile() {
    }

    /**
     * Looks for .env in the working directory and loads any values that are
     * not already set as system properties.
     */
    public static void load() {
        load(Paths.get(System.getProperty("user.dir", "."), ".env"));
    }

    public static void load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                String entry = line.trim();
                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }
                if (entry.startsWith("export ")) {
                    entry = entry.substring("export ".length()).trim();
                }

                int equals = entry.indexOf('=');
                if (equals < 1) {
                    continue;
                }

                String key = entry.substring(0, equals).trim();
                String value = entry.substring(equals + 1).trim();
                value = unquote(value);

                // Real env vars are read first by DatabaseConnection; explicit
                // -D properties should beat values from the file.
                if (System.getProperty(key) == null) {
                    System.setProperty(key, value);
                }
            }
        } catch (IOException e) {
            System.err.println("  Could not read .env file: " + e.getMessage());
        }
    }

    /**
     * Writes or updates one KEY=VALUE line in the .env file, keeping comments
     * and other settings intact.
     */
    public static void set(String key, String value) {
        Path file = Paths.get(System.getProperty("user.dir", "."), ".env");
        List<String> lines = new ArrayList<>();
        if (Files.isRegularFile(file)) {
            try {
                lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                System.err.println("  Could not read .env file: " + e.getMessage());
                return;
            }
        }

        String target = key + "=" + (value == null ? "" : value);
        boolean replaced = false;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            String bare = line.startsWith("export ") ? line.substring(7).trim() : line;
            if (bare.startsWith(key + "=")) {
                lines.set(i, target);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            lines.add(target);
        }

        try {
            Files.write(file, lines, StandardCharsets.UTF_8);
            System.setProperty(key, value == null ? "" : value);
        } catch (IOException e) {
            System.err.println("  Could not write .env file: " + e.getMessage());
        }
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
