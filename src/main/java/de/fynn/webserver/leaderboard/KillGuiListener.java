package de.fynn.webserver.leaderboard;

import de.fynn.webserver.WebServerPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("deprecation")
public class KillGuiListener implements Listener {

    private final WebServerPlugin plugin;
    private final KillGuiBuilder builder = new KillGuiBuilder();
    private final Map<UUID, BukkitTask> liveTasks = new ConcurrentHashMap<>();

    public KillGuiListener(WebServerPlugin plugin) {
        this.plugin = plugin;
    }

    public void openList(Player player, int page) {
        Inventory inventory = Bukkit.createInventory(
                new KillGuiHolder(KillGuiHolder.Page.LIST, page, null),
                54, KillGuiBuilder.LIST_TITLE);
        builder.buildList(inventory, plugin.getKillStatsService(), page);
        player.openInventory(inventory);
        startLiveRefresh(player);
    }

    public void openDetail(Player player, UUID targetUuid, int returnPage) {
        Inventory inventory = Bukkit.createInventory(
                new KillGuiHolder(KillGuiHolder.Page.DETAIL, returnPage, targetUuid),
                54, KillGuiBuilder.DETAIL_TITLE);
        builder.buildDetail(inventory, plugin.getKillStatsService(), targetUuid, plugin.getKillStatsService().isTrackingWorlds());
        player.openInventory(inventory);
        startLiveRefresh(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof KillGuiHolder guiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) {
            return;
        }

        switch (guiHolder.getPage()) {
            case LIST -> handleListClick(player, guiHolder, slot, event);
            case DETAIL -> handleDetailClick(player, guiHolder, slot);
        }
    }

    private void handleListClick(Player player, KillGuiHolder holder, int slot, InventoryClickEvent event) {
        if (slot == 45) {
            int prevPage = holder.getListPage() - 1;
            if (prevPage >= 0) {
                openList(player, prevPage);
            }
            return;
        }
        if (slot == 53) {
            openList(player, holder.getListPage() + 1);
            return;
        }

        // Click on a player head (slots 0-44)
        if (slot >= 0 && slot <= 44) {
            var item = event.getInventory().getItem(slot);
            if (item != null && item.hasItemMeta() && item.getItemMeta() instanceof SkullMeta skullMeta) {
                if (skullMeta.getOwningPlayer() != null) {
                    UUID targetUuid = skullMeta.getOwningPlayer().getUniqueId();
                    openDetail(player, targetUuid, holder.getListPage());
                }
            }
        }
    }

    private void handleDetailClick(Player player, KillGuiHolder holder, int slot) {
        UUID targetUuid = holder.getSelectedPlayer();
        if (targetUuid == null) {
            return;
        }
        var killService = plugin.getKillStatsService();

        switch (slot) {
            case 37 -> {
                killService.removeKills(targetUuid, 10);
                refreshDetail(player, holder);
            }
            case 38 -> {
                killService.removeKills(targetUuid, 1);
                refreshDetail(player, holder);
            }
            case 40 -> {
                killService.setKills(targetUuid, 0);
                refreshDetail(player, holder);
            }
            case 42 -> {
                killService.addKills(targetUuid, 1);
                refreshDetail(player, holder);
            }
            case 43 -> {
                killService.addKills(targetUuid, 10);
                refreshDetail(player, holder);
            }
            case 49 -> {
                openList(player, holder.getListPage());
            }
        }
    }

    private void refreshDetail(Player player, KillGuiHolder holder) {
        Inventory top = player.getOpenInventory().getTopInventory();
        builder.buildDetail(top, plugin.getKillStatsService(), holder.getSelectedPlayer(), plugin.getKillStatsService().isTrackingWorlds());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof KillGuiHolder) {
            stopLiveRefresh(event.getPlayer().getUniqueId());
        }
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
                if (!(top.getHolder() instanceof KillGuiHolder holder)) {
                    cancel();
                    return;
                }
                var killService = plugin.getKillStatsService();
                if (holder.getPage() == KillGuiHolder.Page.LIST) {
                    builder.buildList(top, killService, holder.getListPage());
                } else if (holder.getPage() == KillGuiHolder.Page.DETAIL && holder.getSelectedPlayer() != null) {
                    builder.buildDetail(top, killService, holder.getSelectedPlayer(), killService.isTrackingWorlds());
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
