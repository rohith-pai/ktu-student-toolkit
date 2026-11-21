package com.ktutoolkit.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates and reuses a JDBC connection to the Supabase PostgreSQL database.
 *
 * <p>Connection details are read from environment variables first and from
 * JVM system properties as a fallback, so no credentials are hard-coded in
 * the source code.</p>
 */
public class DatabaseConnection implements AutoCloseable {
    private static boolean disabledByUser;

    private Connection connection;

    /**
     * Disables JDBC for this program run (the user chose live-fetch mode).
     */
    public static void disableJdbc() {
        disabledByUser = true;
    }

    /**
     * Returns true when DB_URL, DB_USER and DB_PASSWORD are available.
     * The rest of the toolkit works fine when this is false; only the
     * optional Supabase sync needs these settings.
     */
    public static boolean isConfigured() {
        return missingSettings().isEmpty();
    }

    /**
     * Returns the names of the DB settings that are missing from this process.
     */
    public static List<String> missingSettings() {
        List<String> missing = new ArrayList<>();
        if (disabledByUser) {
            missing.add("JDBC mode disabled for this run");
            return missing;
        }
        if (readSetting("DB_URL", "db.url").isEmpty()) {
            missing.add("DB_URL");
        }
        if (readSetting("DB_USER", "db.user").isEmpty()) {
            missing.add("DB_USER");
        }
        if (readSetting("DB_PASSWORD", "db.password").isEmpty()) {
            missing.add("DB_PASSWORD");
        }
        return missing;
    }

    public Connection connect() throws SQLException {
        if (disabledByUser) {
            throw new SQLException("JDBC mode is disabled for this session.");
        }
        if (connection != null && !connection.isClosed()) {
            return connection;
        }

        String url = readSetting("DB_URL", "db.url");
        String user = readSetting("DB_USER", "db.user");
        String password = readSetting("DB_PASSWORD", "db.password");

        if (url.isEmpty() || user.isEmpty() || password.isEmpty()) {
            throw new SQLException(
                    "Database configuration missing. Set DB_URL, DB_USER and DB_PASSWORD.");
        }

        connection = DriverManager.getConnection(withDriverOptions(url), user, password);
        return connection;
    }

    @Override
    public void close() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Closing a broken connection is best-effort.
        } finally {
            connection = null;
        }
    }

    /**
     * Supabase's transaction pooler reuses backend connections, which can make
     * the driver reuse server-side statement names ("S_1 already exists").
     * Disabling server-side prepared statements avoids that.
     */
    private static String withDriverOptions(String url) {
        if (url == null || url.isBlank() || url.contains("prepareThreshold=")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "prepareThreshold=0";
    }

    private static String readSetting(String environmentName, String propertyName) {
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
            // Loaded from .env, or passed as -DDB_URL=...
            value = System.getProperty(environmentName);
        }
        if (value == null || value.isBlank()) {
            value = System.getProperty(propertyName);
        }
        return value == null ? "" : value.trim();
    }
}
