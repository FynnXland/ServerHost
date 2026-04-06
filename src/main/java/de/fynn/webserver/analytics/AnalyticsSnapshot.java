package de.fynn.webserver.analytics;

import de.fynn.webserver.util.JsonUtil;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record AnalyticsSnapshot(
        long totalRequests,
        long totalErrors,
        long totalBytes,
        long averageDurationNanos,
        long minDurationNanos,
        long maxDurationNanos,
        Map<Integer, Long> statusCounts,
        Map<String, Long> methodCounts,
        Map<String, Long> topPaths,
        Map<LocalDate, Long> dailyCounts,
        List<AnalyticsService.RequestRecord> recentRequests
) {
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"totalRequests\":").append(totalRequests).append(",");
        json.append("\"totalErrors\":").append(totalErrors).append(",");
        json.append("\"totalBytes\":").append(totalBytes).append(",");
        json.append("\"averageDurationNanos\":").append(averageDurationNanos).append(",");
        json.append("\"minDurationNanos\":").append(minDurationNanos).append(",");
        json.append("\"maxDurationNanos\":").append(maxDurationNanos).append(",");
        json.append("\"statusCounts\":").append(intMapToJson(statusCounts)).append(",");
        json.append("\"methodCounts\":").append(stringMapToJson(methodCounts)).append(",");
        json.append("\"topPaths\":").append(stringMapToJson(topPaths)).append(",");
        json.append("\"dailyCounts\":").append(dailyMapToJson(dailyCounts)).append(",");
        json.append("\"recentRequests\":").append(recentToJson(recentRequests));
        json.append("}");
        return json.toString();
    }

    private static String intMapToJson(Map<Integer, Long> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<Integer, Long> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private static String stringMapToJson(Map<String, Long> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Long> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(JsonUtil.escape(entry.getKey())).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private static String dailyMapToJson(Map<LocalDate, Long> map) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<LocalDate, Long> entry : map.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .toList()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        json.append("}");
        return json.toString();
    }

    private static String recentToJson(List<AnalyticsService.RequestRecord> records) {
        StringBuilder json = new StringBuilder("[");
        boolean first = true;
        for (AnalyticsService.RequestRecord record : records) {
            if (!first) {
                json.append(",");
            }
            json.append("{")
                    .append("\"method\":\"").append(JsonUtil.escape(record.method())).append("\",")
                    .append("\"path\":\"").append(JsonUtil.escape(record.path())).append("\",")
                    .append("\"statusCode\":").append(record.statusCode()).append(",")
                    .append("\"durationNanos\":").append(record.durationNanos()).append(",")
                    .append("\"bytes\":").append(record.bytes()).append(",")
                    .append("\"remoteAddress\":\"").append(JsonUtil.escape(record.remoteAddress())).append("\",")
                    .append("\"timestampMillis\":").append(record.timestampMillis())
                    .append("}");
            first = false;
        }
        json.append("]");
        return json.toString();
    }

}
