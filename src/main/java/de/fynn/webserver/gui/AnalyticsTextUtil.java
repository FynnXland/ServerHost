package de.fynn.webserver.gui;

public final class AnalyticsTextUtil {

    private AnalyticsTextUtil() {
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        return String.format("%.1f MB", mb);
    }

    public static String formatDuration(long nanos) {
        if (nanos < 1_000_000) {
            return String.format("%.2f ms", nanos / 1_000_000.0);
        }
        return String.format("%.1f ms", nanos / 1_000_000.0);
    }
}
