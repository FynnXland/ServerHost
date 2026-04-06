package de.fynn.webserver.leaderboard;

import de.fynn.webserver.util.JsonUtil;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class KillStatsService implements Listener {

    private final ConcurrentHashMap<UUID, Integer> killCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> nameCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Integer>> worldKillCache = new ConcurrentHashMap<>();
    private boolean trackWorlds;

    public KillStatsService(boolean trackWorlds) {
        this.trackWorlds = trackWorlds;
    }

    public boolean isTrackingWorlds() {
        return trackWorlds;
    }

    public void setTrackWorlds(boolean trackWorlds) {
        this.trackWorlds = trackWorlds;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        killCache.putIfAbsent(player.getUniqueId(), player.getStatistic(Statistic.PLAYER_KILLS));
        nameCache.put(player.getUniqueId(), player.getName());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            killCache.merge(killer.getUniqueId(), 1, (a, b) -> a + b);
            nameCache.put(killer.getUniqueId(), killer.getName());

            if (trackWorlds) {
                String worldName = killer.getWorld().getName();
                worldKillCache.computeIfAbsent(killer.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .merge(worldName, 1, (a, b) -> a + b);
            }
        }
    }

    public Map<UUID, Integer> getKillCache() {
        return killCache;
    }

    public java.util.List<Map.Entry<UUID, Integer>> getAllEntriesSorted() {
        return killCache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .toList();
    }

    public String getPlayerName(UUID uuid) {
        return nameCache.getOrDefault(uuid, "Unbekannt");
    }

    public void setKills(UUID uuid, int kills) {
        killCache.put(uuid, Math.max(0, kills));
    }

    public void addKills(UUID uuid, int amount) {
        killCache.merge(uuid, amount, (a, b) -> a + b);
    }

    public void removeKills(UUID uuid, int amount) {
        killCache.computeIfPresent(uuid, (k, v) -> Math.max(0, v - amount));
    }

    public Map<String, Integer> getWorldBreakdown(UUID uuid) {
        ConcurrentHashMap<String, Integer> worlds = worldKillCache.get(uuid);
        if (worlds == null) {
            return Map.of();
        }
        return Map.copyOf(worlds);
    }

    public UUID findUuidByName(String name) {
        for (Map.Entry<UUID, String> entry : nameCache.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(name)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public String buildLeaderboardJson(int limit) {
        var sorted = killCache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .toList();

        StringBuilder json = new StringBuilder();
        json.append("{\"leaderboard\":[");
        boolean first = true;
        for (Map.Entry<UUID, Integer> entry : sorted) {
            if (!first) {
                json.append(",");
            }
            String name = nameCache.getOrDefault(entry.getKey(), "Unbekannt");
            json.append("{")
                    .append("\"uuid\":\"").append(entry.getKey()).append("\",")
                    .append("\"name\":\"").append(JsonUtil.escape(name)).append("\",")
                    .append("\"kills\":").append(entry.getValue())
                    .append("}");
            first = false;
        }
        json.append("]}");
        return json.toString();
    }

}
