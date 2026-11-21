package com.ktutoolkit.settings;

import com.ktutoolkit.core.EnvFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;

/**
 * Persistent configuration for the toolkit.
 *
 * <p>ETLab credentials always come from the project .env file
 * (ETLAB_USERNAME, ETLAB_PASSWORD, ETLAB_SESSION_TOKEN). The password is
 * plain text or lightly obfuscated, NOT securely encrypted.</p>
 *
 * <p>The legacy home-directory config file is now used only for the optional
 * PYQ papers folder setting.</p>
 */
public final class AppConfig {
    private static final String PYQ_DIR_KEY = "pyq.downloadDir";

    private static final String ENV_USERNAME = "ETLAB_USERNAME";
    private static final String ENV_PASSWORD = "ETLAB_PASSWORD";
    private static final String ENV_TOKEN = "ETLAB_SESSION_TOKEN";

    private final Path configFile;
    private final Properties properties = new Properties();

    public AppConfig() {
        this(defaultConfigPath());
    }

    public AppConfig(Path configFile) {
        this.configFile = configFile;
        load();
    }

    private static Path defaultConfigPath() {
        String override = System.getProperty("ktu.config");
        if (override != null && !override.isBlank()) {
            return Paths.get(override);
        }
        return Paths.get(System.getProperty("user.home"), ".ktu-student-toolkit", "config.properties");
    }

    /**
     * Reloads the PYQ folder setting from disk.
     */
    public void reload() {
        properties.clear();
        load();
    }

    public String getUsername() {
        return setting(ENV_USERNAME);
    }

    public String getPassword() {
        return decode(setting(ENV_PASSWORD));
    }

    public String getSessionToken() {
        return setting(ENV_TOKEN);
    }

    public boolean hasCredentials() {
        return !getUsername().isEmpty() && !getPassword().isEmpty();
    }

    /**
     * Returns the user-selected PYQ papers folder, or an empty string when
     * the default (~/Downloads/papers) should be used.
     */
    public String getPyqDownloadDirectory() {
        return properties.getProperty(PYQ_DIR_KEY, "").trim();
    }

    public void setPyqDownloadDirectory(String path) {
        if (path == null || path.isBlank()) {
            properties.remove(PYQ_DIR_KEY);
        } else {
            properties.setProperty(PYQ_DIR_KEY, path.trim());
        }
        save();
    }

    public void saveSession(String username, String password, String sessionToken) {
        EnvFile.set(ENV_USERNAME, username == null ? "" : username.trim());
        EnvFile.set(ENV_PASSWORD, password == null ? "" : password);
        EnvFile.set(ENV_TOKEN, sessionToken == null ? "" : sessionToken.trim());
    }

    public void saveSessionToken(String sessionToken) {
        EnvFile.set(ENV_TOKEN, sessionToken == null ? "" : sessionToken.trim());
    }

    public void clearSessionToken() {
        EnvFile.set(ENV_TOKEN, "");
    }

    public void clearCredentials() {
        EnvFile.set(ENV_USERNAME, "");
        EnvFile.set(ENV_PASSWORD, "");
        EnvFile.set(ENV_TOKEN, "");
    }

    private void load() {
        if (configFile == null || !Files.isRegularFile(configFile)) {
            return;
        }
        try (InputStream in = Files.newInputStream(configFile)) {
            properties.load(in);
        } catch (IOException e) {
            System.err.println("  Could not read configuration: " + e.getMessage());
        }
    }

    private synchronized void save() {
        Path absolute = configFile.toAbsolutePath();
        Path parent = absolute.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path temp = Files.createTempFile(parent, "config", ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) {
                properties.store(out, "KTU Student Toolkit configuration");
            }
            try {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveUnsupported) {
                Files.move(temp, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictPermissions(absolute);
        } catch (IOException e) {
            System.err.println("  Could not save configuration: " + e.getMessage());
        }
    }

    private static void restrictPermissions(Path file) {
        try {
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(file,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            }
        } catch (IOException | UnsupportedOperationException ignored) {
            // Windows / unsupported file systems: keep default permissions.
        }
    }

    private static String setting(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            value = System.getProperty(key);
        }
        return value == null ? "" : value.trim();
    }

    private static String decode(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            return value;
        }
        if (!Base64.getEncoder().encodeToString(decoded).equals(value)) {
            return value;
        }
        String text = new String(decoded, StandardCharsets.UTF_8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD' || c < 0x20 || c > 0x7E) {
                return value;
            }
        }
        return text;
    }
}
