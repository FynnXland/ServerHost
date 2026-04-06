package de.fynn.webserver.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class AnalyticsService {

    private final int recentLimit;
    private final LongAdder totalRequests = new LongAdder();
    private final LongAdder totalErrors = new LongAdder();
    private final LongAdder totalBytes = new LongAdder();
    private final LongAdder totalDurationNanos = new LongAdder();
    private final AtomicLong maxDurationNanos = new AtomicLong(0);
    private final AtomicLong minDurationNanos = new AtomicLong(Long.MAX_VALUE);
    private final Map<Integer, LongAdder> statusCounts = new HashMap<>();
    private final Map<String, LongAdder> methodCounts = new HashMap<>();
    private final Map<String, LongAdder> pathCounts = new HashMap<>();
    private final Map<LocalDate, LongAdder> dailyCounts = new HashMap<>();
    private final Deque<RequestRecord> recentRequests = new ArrayDeque<>();

    private static final int MAX_PATH_ENTRIES = 500;

    public AnalyticsService(int recentLimit) {
        this.recentLimit = Math.max(10, recentLimit);
    }

    public synchronized void recordRequest(String method, String path, int statusCode, long durationNanos, long bytes, String remoteAddress) {
        totalRequests.increment();
        totalBytes.add(Math.max(0, bytes));
        totalDurationNanos.add(Math.max(0, durationNanos));
        if (statusCode >= 400) {
            totalErrors.increment();
        }

        statusCounts.computeIfAbsent(statusCode, ignored -> new LongAdder()).increment();
        methodCounts.computeIfAbsent(sanitizeKey(method), ignored -> new LongAdder()).increment();
        if (pathCounts.size() < MAX_PATH_ENTRIES) {
            pathCounts.computeIfAbsent(sanitizePath(path), ignored -> new LongAdder()).increment();
        } else {
            LongAdder existing = pathCounts.get(sanitizePath(path));
            if (existing != null) {
                existing.increment();
            }
        }
        dailyCounts.computeIfAbsent(LocalDate.now(), ignored -> new LongAdder()).increment();

        maxDurationNanos.updateAndGet(current -> Math.max(current, Math.max(0, durationNanos)));
        minDurationNanos.updateAndGet(current -> Math.min(current, Math.max(0, durationNanos)));

        recentRequests.addFirst(new RequestRecord(
                sanitizeKey(method),
                sanitizePath(path),
                statusCode,
                Math.max(0, durationNanos),
                Math.max(0, bytes),
                remoteAddress == null ? "unknown" : remoteAddress,
                Instant.now().toEpochMilli()
        ));
        while (recentRequests.size() > recentLimit) {
            recentRequests.removeLast();
        }
    }

    public synchronized AnalyticsSnapshot snapshot() {
        long requestCount = totalRequests.sum();
        long durationSum = totalDurationNanos.sum();
        long minNanos = minDurationNanos.get() == Long.MAX_VALUE ? 0 : minDurationNanos.get();
        long maxNanos = maxDurationNanos.get();

        return new AnalyticsSnapshot(
                requestCount,
                totalErrors.sum(),
                totalBytes.sum(),
                requestCount == 0 ? 0 : durationSum / requestCount,
                minNanos,
                maxNanos,
                convertMap(statusCounts),
                convertMap(methodCounts),
                topEntries(pathCounts, 8),
                convertDailyMap(),
                new ArrayList<>(recentRequests)
        );
    }

    public synchronized void reset() {
        totalRequests.reset();
        totalErrors.reset();
        totalBytes.reset();
        totalDurationNanos.reset();
        maxDurationNanos.set(0);
        minDurationNanos.set(Long.MAX_VALUE);
        statusCounts.clear();
        methodCounts.clear();
        pathCounts.clear();
        dailyCounts.clear();
        recentRequests.clear();
    }

    private static String sanitizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.length() > 80 ? path.substring(0, 80) + "..." : path;
    }

    private static String sanitizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        return value.toUpperCase();
    }

    private static <K> Map<K, Long> convertMap(Map<K, LongAdder> source) {
        Map<K, Long> out = new HashMap<>();
        for (Map.Entry<K, LongAdder> entry : source.entrySet()) {
            out.put(entry.getKey(), entry.getValue().sum());
        }
        return out;
    }

    private Map<LocalDate, Long> convertDailyMap() {
        Map<LocalDate, Long> out = new HashMap<>();
        for (Map.Entry<LocalDate, LongAdder> entry : dailyCounts.entrySet()) {
            out.put(entry.getKey(), entry.getValue().sum());
        }
        return out;
    }

    private static Map<String, Long> topEntries(Map<String, LongAdder> source, int limit) {
        List<Map.Entry<String, LongAdder>> sorted = new ArrayList<>(source.entrySet());
        sorted.sort(Comparator.comparingLong((Map.Entry<String, LongAdder> e) -> e.getValue().sum()).reversed());
        Map<String, Long> top = new HashMap<>();
        int count = 0;
        for (Map.Entry<String, LongAdder> entry : sorted) {
            top.put(entry.getKey(), entry.getValue().sum());
            count++;
            if (count >= limit) {
                break;
            }
        }
        return top;
    }

    public record RequestRecord(
            String method,
            String path,
            int statusCode,
            long durationNanos,
            long bytes,
            String remoteAddress,
            long timestampMillis
    ) {
        public String timeText() {
            return Instant.ofEpochMilli(timestampMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .withNano(0)
                    .toString();
        }
    }
}
