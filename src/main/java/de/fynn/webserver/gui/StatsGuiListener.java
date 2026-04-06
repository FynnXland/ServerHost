package de.fynn.webserver.gui;

import de.fynn.webserver.WebServerPlugin;
import de.fynn.webserver.stats.RuntimeStatsSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class StatsGuiListener implements Listener {

    private final WebServerPlugin plugin;
    private final Map<UUID, BukkitTask> liveTasks = new ConcurrentHashMap<>();

    public StatsGuiListener(WebServerPlugin plugin) {
        this.plugin = plugin;
    }

    public void openDashboard(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            RuntimeStatsSnapshot snapshot = plugin.getRuntimeStatsService().snapshot(plugin.getAnalyticsService().snapshot());
            boolean online = plugin.getWebServer() != null && plugin.getWebServer().isRunning();
            int port = plugin.getConfig().getInt("port", 8080);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inventory = Bukkit.createInventory(new StatsGuiHolder(StatsGuiHolder.Page.DASHBOARD), 27, StatsGuiBuilder.DASHBOARD_TITLE);
                plugin.getStatsGuiBuilder().buildDashboard(inventory, snapshot, online, port);
                player.openInventory(inventory);
                startLiveRefresh(player);
            });
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof StatsGuiHolder guiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        switch (guiHolder.getPage()) {
            case DASHBOARD -> {
                if (slot == 11) {
                    openTraffic(player);
                } else if (slot == 15) {
                    openSystem(player);
                } else if (slot == 22) {
                    openSecurity(player);
                }
            }
            case TRAFFIC, SYSTEM, SECURITY -> {
                if (slot == 49) {
                    openDashboard(player);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof StatsGuiHolder) {
            stopLiveRefresh(event.getPlayer().getUniqueId());
        }
    }

    private void openTraffic(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            RuntimeStatsSnapshot snapshot = plugin.getRuntimeStatsService().snapshot(plugin.getAnalyticsService().snapshot());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inventory = Bukkit.createInventory(new StatsGuiHolder(StatsGuiHolder.Page.TRAFFIC), 54, StatsGuiBuilder.TRAFFIC_TITLE);
                plugin.getStatsGuiBuilder().buildTrafficDetails(inventory, snapshot);
                player.openInventory(inventory);
            });
        });
    }

    private void openSystem(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            RuntimeStatsSnapshot snapshot = plugin.getRuntimeStatsService().snapshot(plugin.getAnalyticsService().snapshot());
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inventory = Bukkit.createInventory(new StatsGuiHolder(StatsGuiHolder.Page.SYSTEM), 54, StatsGuiBuilder.SYSTEM_TITLE);
                plugin.getStatsGuiBuilder().buildSystemDetails(inventory, snapshot);
                player.openInventory(inventory);
            });
        });
    }

    private void openSecurity(Player player) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            RuntimeStatsSnapshot snapshot = plugin.getRuntimeStatsService().snapshot(plugin.getAnalyticsService().snapshot());
            var access = plugin.getWebServer() == null ? java.util.List.<String>of() : plugin.getWebServer().getRecentAccessLog(16);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Inventory inventory = Bukkit.createInventory(new StatsGuiHolder(StatsGuiHolder.Page.SECURITY), 54, StatsGuiBuilder.SECURITY_TITLE);
                plugin.getStatsGuiBuilder().buildSecurityDetails(inventory, snapshot, access);
                player.openInventory(inventory);
            });
        });
    }

    private void startLiveRefresh(Player player) {
        stopLiveRefresh(player.getUniqueId());
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }
                Inventory top = player.getOpenInventory().getTopInventory();
                if (!(top.getHolder() instanceof StatsGuiHolder holder)) {
                    cancel();
                    return;
                }

                RuntimeStatsSnapshot snapshot = plugin.getRuntimeStatsService().snapshot(plugin.getAnalyticsService().snapshot());
                if (holder.getPage() == StatsGuiHolder.Page.DASHBOARD) {
                    plugin.getStatsGuiBuilder().buildDashboard(top, snapshot, plugin.getWebServer() != null && plugin.getWebServer().isRunning(), plugin.getConfig().getInt("port", 8080));
                } else if (holder.getPage() == StatsGuiHolder.Page.TRAFFIC) {
                    plugin.getStatsGuiBuilder().buildTrafficDetails(top, snapshot);
                } else if (holder.getPage() == StatsGuiHolder.Page.SYSTEM) {
                    plugin.getStatsGuiBuilder().buildSystemDetails(top, snapshot);
                } else if (holder.getPage() == StatsGuiHolder.Page.SECURITY) {
                    plugin.getStatsGuiBuilder().buildSecurityDetails(top, snapshot, plugin.getWebServer() == null ? java.util.List.of() : plugin.getWebServer().getRecentAccessLog(16));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
        liveTasks.put(player.getUniqueId(), task);
    }

    private void stopLiveRefresh(UUID playerId) {
        BukkitTask task = liveTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }
}
