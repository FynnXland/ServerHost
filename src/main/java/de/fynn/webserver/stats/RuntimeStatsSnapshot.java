package de.fynn.webserver.stats;

import de.fynn.webserver.analytics.AnalyticsSnapshot;
import de.fynn.webserver.util.JsonUtil;

import java.util.List;
import java.util.Locale;

public record RuntimeStatsSnapshot(
        String timestamp,
        int onlinePlayers,
        int maxPlayers,
        String motd,
        String version,
        String bukkitVersion,
        String serverName,
        int loadedWorlds,
        int loadedChunks,
        int loadedEntities,
        String osName,
        String osVersion,
        String osArch,
        String javaVersion,
        String cpuModel,
        int cpuLogicalCores,
        String gpuModel,
        double cpuPercent,
        long ramBytes,
        long maxMemoryBytes,
        int onlineMode,
        long storageBytes,
        long storageTotalBytes,
        long storageTotalGb,
        double tps,
        AnalyticsSnapshot analytics,
        List<PlayerEntry> players
) {
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"timestamp\":\"").append(timestamp).append("\",");
        json.append("\"server\":{");
        json.append("\"onlinePlayers\":").append(onlinePlayers).append(",");
        json.append("\"maxPlayers\":").append(maxPlayers).append(",");
        json.append("\"motd\":\"").append(JsonUtil.escape(motd)).append("\",");
        json.append("\"version\":\"").append(JsonUtil.escape(version)).append("\",");
        json.append("\"bukkitVersion\":\"").append(JsonUtil.escape(bukkitVersion)).append("\",");
        json.append("\"serverName\":\"").append(JsonUtil.escape(serverName)).append("\",");
        json.append("\"loadedWorlds\":").append(loadedWorlds).append(",");
        json.append("\"loadedChunks\":").append(loadedChunks).append(",");
        json.append("\"loadedEntities\":").append(loadedEntities).append(",");
        json.append("\"osName\":\"").append(JsonUtil.escape(osName)).append("\",");
        json.append("\"osVersion\":\"").append(JsonUtil.escape(osVersion)).append("\",");
        json.append("\"osArch\":\"").append(JsonUtil.escape(osArch)).append("\",");
        json.append("\"javaVersion\":\"").append(JsonUtil.escape(javaVersion)).append("\",");
        json.append("\"cpuModel\":\"").append(JsonUtil.escape(cpuModel)).append("\",");
        json.append("\"cpuLogicalCores\":").append(cpuLogicalCores).append(",");
        json.append("\"gpuModel\":\"").append(JsonUtil.escape(gpuModel)).append("\",");
        json.append("\"tps\":").append(formatDouble(tps)).append(",");
        json.append("\"players\":[");
        List<PlayerEntry> safePlayers = players != null ? players : List.of();
        for (int i = 0; i < safePlayers.size(); i++) {
            PlayerEntry p = safePlayers.get(i);
            json.append("{\"name\":\"").append(JsonUtil.escape(p.name())).append("\",");
            json.append("\"uuid\":\"").append(JsonUtil.escape(p.uuid())).append("\"}");
            if (i < safePlayers.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");
        json.append("},");
        json.append("\"runtime\":{");
        json.append("\"cpuPercent\":").append(formatDouble(cpuPercent)).append(",");
        json.append("\"ramBytes\":").append(ramBytes).append(",");
        json.append("\"ramMb\":").append(ramBytes / (1024 * 1024)).append(",");
        json.append("\"maxMemoryBytes\":").append(maxMemoryBytes).append(",");
        json.append("\"maxMemoryMb\":").append(maxMemoryBytes / (1024 * 1024)).append(",");
        json.append("\"onlineMode\":").append(onlineMode).append(",");
        json.append("\"storageBytes\":").append(storageBytes).append(",");
        json.append("\"storageTotalBytes\":").append(storageTotalBytes).append(",");
        json.append("\"storageTotalGb\":").append(storageTotalGb);
        json.append("},");
        json.append("\"analytics\":").append(analytics.toJson());
        json.append("}");
        return json.toString();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}

