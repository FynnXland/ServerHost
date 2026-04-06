package de.fynn.webserver.debug;

import de.fynn.webserver.WebServerPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class DebugService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private volatile boolean debugEnabled;
    private final int maxEntries;
    private final Deque<String> recentErrors = new ArrayDeque<>();

    public DebugService(boolean debugEnabled, int maxEntries) {
        this.debugEnabled = debugEnabled;
        this.maxEntries = Math.max(10, maxEntries);
    }

    public void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }

    public synchronized void recordError(WebServerPlugin plugin, String context, Exception exception) {
        String message = "[" + TIME_FORMAT.format(Instant.now()) + "] " + context + ": " + exception.getMessage();
        recentErrors.addFirst(message);
        while (recentErrors.size() > maxEntries) {
            recentErrors.removeLast();
        }

        plugin.getLogger().warning("[Debug] " + context + " - " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
        if (debugEnabled) {
            plugin.getLogger().warning("[Debug] Stacktrace for " + context);
            for (StackTraceElement element : exception.getStackTrace()) {
                plugin.getLogger().warning("[Debug]   at " + element);
            }
            if (exception.getCause() != null) {
                plugin.getLogger().warning("[Debug] Caused by: " + exception.getCause());
            }
        }
    }

    public synchronized List<String> getRecentErrors(int limit) {
        List<String> errors = new ArrayList<>();
        int count = 0;
        for (String entry : recentErrors) {
            errors.add(entry);
            count++;
            if (count >= Math.max(1, limit)) {
                break;
            }
        }
        return errors;
    }
}
