package de.fynn.webserver.gui;

import de.fynn.webserver.analytics.AnalyticsSnapshot;
import de.fynn.webserver.stats.RuntimeStatsSnapshot;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class StatsGuiBuilder {

    public static final String DASHBOARD_TITLE = ChatColor.DARK_AQUA + "Serverhost Dashboard";
    public static final String TRAFFIC_TITLE = ChatColor.DARK_AQUA + "Traffic Analytics";
    public static final String SYSTEM_TITLE = ChatColor.DARK_GREEN + "System Stats";
    public static final String SECURITY_TITLE = ChatColor.DARK_RED + "Security & Errors";

    public Inventory buildDashboard(Inventory inventory, RuntimeStatsSnapshot snapshot, boolean serverOnline, int port) {
        inventory.clear();

        inventory.setItem(11, createItem(Material.MAP, ChatColor.AQUA + "" + ChatColor.BOLD + "Traffic Analytics", List.of(
                ChatColor.GRAY + "Klicke für Details zu:",
                ChatColor.YELLOW + "• Total Requests",
                ChatColor.YELLOW + "• Top Pfade",
                ChatColor.YELLOW + "• Status-Codes"
        )));

        Material statusMaterial = serverOnline ? Material.BEACON : Material.REDSTONE_BLOCK;
        inventory.setItem(13, createItem(statusMaterial, ChatColor.GOLD + "" + ChatColor.BOLD + "Webserver Status", List.of(
                ChatColor.GRAY + "Port: " + ChatColor.WHITE + port,
                ChatColor.GRAY + "Base-URL: " + ChatColor.WHITE + "http://<host>:" + port,
                ChatColor.GRAY + "Status: " + (serverOnline ? ChatColor.GREEN + "Online" : ChatColor.RED + "Offline"),
                ChatColor.GRAY + "SSL: " + ChatColor.YELLOW + "Inaktiv"
        )));

        inventory.setItem(15, createItem(Material.COMPARATOR, ChatColor.GREEN + "" + ChatColor.BOLD + "System Stats", List.of(
                ChatColor.GRAY + "Klicke für Details zu:",
                ChatColor.YELLOW + "• TPS & CPU",
                ChatColor.YELLOW + "• RAM Auslastung",
                ChatColor.YELLOW + "• Hardware Infos"
        )));

        long totalSecurityEvents = snapshot.analytics().statusCounts().entrySet().stream()
                .filter(e -> e.getKey() >= 400)
                .mapToLong(Map.Entry::getValue)
                .sum();
        inventory.setItem(22, createItem(Material.SHIELD, ChatColor.RED + "" + ChatColor.BOLD + "Security & Errors", List.of(
                ChatColor.GRAY + "Rate-Limit/Errors im Blick",
                ChatColor.GRAY + "4xx/5xx gesamt: " + ChatColor.WHITE + totalSecurityEvents,
                ChatColor.GRAY + "Klicke für Details"
        )));

        return inventory;
    }

    public Inventory buildTrafficDetails(Inventory inventory, RuntimeStatsSnapshot snapshot) {
        inventory.clear();
        AnalyticsSnapshot analytics = snapshot.analytics();

        inventory.setItem(4, createItem(Material.PAPER, ChatColor.AQUA + "" + ChatColor.BOLD + "Traffic Summary", List.of(
                ChatColor.GRAY + "Requests gesamt: " + ChatColor.YELLOW + analytics.totalRequests(),
                ChatColor.GRAY + "Requests heute: " + ChatColor.YELLOW + analytics.dailyCounts().getOrDefault(java.time.LocalDate.now(), 0L),
                ChatColor.GRAY + "Errors gesamt: " + ChatColor.RED + analytics.totalErrors()
        )));

        List<Map.Entry<String, Long>> topPaths = analytics.topPaths().entrySet().stream()
                .sorted(Comparator.comparingLong((Map.Entry<String, Long> e) -> e.getValue()).reversed())
                .toList();
        int[] slots = {19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < Math.min(slots.length, topPaths.size()); i++) {
            Map.Entry<String, Long> path = topPaths.get(i);
            double share = analytics.totalRequests() == 0 ? 0 : (path.getValue() * 100.0 / analytics.totalRequests());
            inventory.setItem(slots[i], createItem(Material.OAK_SIGN, ChatColor.GOLD + "Top Pfad #" + (i + 1), List.of(
                    ChatColor.GRAY + "Pfad: " + ChatColor.WHITE + path.getKey(),
                    ChatColor.GRAY + "Aufrufe: " + ChatColor.YELLOW + path.getValue(),
                    ChatColor.GRAY + "Anteil: " + ChatColor.GREEN + String.format("%.1f%%", share)
            )));
        }

        long success2xx = sumStatusRange(analytics.statusCounts(), 200, 299);
        long client4xx = sumStatusRange(analytics.statusCounts(), 400, 499);
        long server5xx = sumStatusRange(analytics.statusCounts(), 500, 599);

        inventory.setItem(10, createStatusItem(Material.LIME_DYE, ChatColor.GREEN + "2xx Erfolgreich", success2xx));
        inventory.setItem(11, createStatusItem(Material.YELLOW_DYE, ChatColor.YELLOW + "4xx Client Errors", client4xx));
        inventory.setItem(12, createStatusItem(Material.RED_DYE, ChatColor.RED + "5xx Server Errors", server5xx));
        inventory.setItem(49, createBackItem());
        return inventory;
    }

    public Inventory buildSystemDetails(Inventory inventory, RuntimeStatsSnapshot snapshot) {
        inventory.clear();
        inventory.setItem(10, createItem(Material.CLOCK, ChatColor.GREEN + "" + ChatColor.BOLD + "Performance", List.of(
                ChatColor.GRAY + "TPS: " + ChatColor.WHITE + String.format("%.2f", snapshot.tps()),
                ChatColor.GRAY + "CPU: " + ChatColor.WHITE + String.format("%.2f%%", snapshot.cpuPercent()),
                ChatColor.GRAY + "RAM: " + ChatColor.WHITE + (snapshot.ramBytes() / 1024 / 1024) + " MB"
        )));
        inventory.setItem(13, createItem(Material.OBSERVER, ChatColor.AQUA + "" + ChatColor.BOLD + "Hardware", List.of(
                ChatColor.GRAY + "CPU: " + ChatColor.WHITE + snapshot.cpuModel(),
                ChatColor.GRAY + "Threads: " + ChatColor.WHITE + snapshot.cpuLogicalCores(),
                ChatColor.GRAY + "GPU: " + ChatColor.WHITE + snapshot.gpuModel()
        )));
        inventory.setItem(16, createItem(Material.GRASS_BLOCK, ChatColor.YELLOW + "" + ChatColor.BOLD + "Minecraft", List.of(
                ChatColor.GRAY + "Spieler: " + ChatColor.WHITE + snapshot.onlinePlayers() + "/" + snapshot.maxPlayers(),
                ChatColor.GRAY + "Welten: " + ChatColor.WHITE + snapshot.loadedWorlds(),
                ChatColor.GRAY + "Chunks: " + ChatColor.WHITE + snapshot.loadedChunks(),
                ChatColor.GRAY + "Entities: " + ChatColor.WHITE + snapshot.loadedEntities()
        )));
        inventory.setItem(49, createBackItem());
        return inventory;
    }

    public Inventory buildSecurityDetails(Inventory inventory, RuntimeStatsSnapshot snapshot, List<String> recentAccessLog) {
        inventory.clear();
        long tooMany = snapshot.analytics().statusCounts().getOrDefault(429, 0L);
        long notFound = snapshot.analytics().statusCounts().getOrDefault(404, 0L);
        long internal = snapshot.analytics().statusCounts().getOrDefault(500, 0L);

        inventory.setItem(10, createItem(Material.SHIELD, ChatColor.RED + "" + ChatColor.BOLD + "Security Summary", List.of(
                ChatColor.GRAY + "429 (Rate-Limit): " + ChatColor.WHITE + tooMany,
                ChatColor.GRAY + "404 Errors: " + ChatColor.WHITE + notFound,
                ChatColor.GRAY + "500 Errors: " + ChatColor.WHITE + internal
        )));

        int slot = 19;
        for (String entry : recentAccessLog) {
            if (slot > 34) {
                break;
            }
            inventory.setItem(slot++, createItem(Material.BOOK, ChatColor.GRAY + "Access-Log", List.of(
                    ChatColor.WHITE + trim(entry, 80)
            )));
        }
        inventory.setItem(49, createBackItem());
        return inventory;
    }

    private static ItemStack createStatusItem(Material material, String name, long value) {
        int amount = (int) Math.max(1, Math.min(64, value));
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + "Anzahl: " + ChatColor.WHITE + value));
        item.setItemMeta(meta);
        return item;
    }

    private static long sumStatusRange(Map<Integer, Long> statuses, int from, int to) {
        return statuses.entrySet().stream()
                .filter(e -> e.getKey() >= from && e.getKey() <= to)
                .mapToLong(Map.Entry::getValue)
                .sum();
    }

    private static ItemStack createBackItem() {
        return createItem(Material.ARROW, ChatColor.YELLOW + "Zurück", List.of(
                ChatColor.GRAY + "Zurück zum Dashboard"
        ));
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static String trim(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }
}
