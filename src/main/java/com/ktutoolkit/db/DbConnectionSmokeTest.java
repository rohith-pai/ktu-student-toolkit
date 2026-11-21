package com.ktutoolkit.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Small helper to verify that the Supabase JDBC connection works.
 *
 * <p>Run with DB_URL, DB_USER and DB_PASSWORD set.</p>
 */
public final class DbConnectionSmokeTest {
    private DbConnectionSmokeTest() {
    }

    public static void main(String[] args) {
        try (DatabaseConnection database = new DatabaseConnection();
             Connection connection = database.connect();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("select version()")) {
            if (result.next()) {
                System.out.println("JDBC CONNECTION OK: " + result.getString(1));
            }
        } catch (Exception e) {
            System.err.println("JDBC CONNECTION FAILED: " + e.getMessage());
            System.exit(1);
        }
    }
}
