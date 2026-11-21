package com.ktutoolkit.db;

/**
 * Decides whether the dashboard should read from the Supabase cache instead
 * of fetching live from ETLab.
 */
public final class SupabaseCache {
    private SupabaseCache() {
    }

    /**
     * True only when JDBC is enabled for this run and a sync has been done.
     */
    public static boolean hasSyncedData() {
        if (!DatabaseConnection.isConfigured()) {
            return false;
        }
        try (SupabaseSyncRepository repository = new SupabaseSyncRepository()) {
            return repository.hasSyncedData();
        } catch (Exception e) {
            return false;
        }
    }
}
