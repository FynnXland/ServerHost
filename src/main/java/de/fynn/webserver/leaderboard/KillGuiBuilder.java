package de.fynn.webserver.leaderboard;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

@SuppressWarnings("deprecation")
public class KillGuiBuilder {

    public static final String LIST_TITLE = ChatColor.GOLD + "⚔ Kill-Leaderboard";
    public static final String DETAIL_TITLE = ChatColor.GOLD + "⚔ Kill-Details";

    private static final int ITEMS_PER_PAGE = 45; // 5 rows of 9

    /**
     * Builds the paginated player list view.
     * Rows 1-5 (slots 0-44): Player heads sorted by kills
     * Row 6 (slots 45-53): Navigation bar
     */
    public Inventory buildList(Inventory inventory, KillStatsService killService, int page) {
        inventory.clear();
        var entries = killService.getAllEntriesSorted();
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        int startIndex = safePage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, entries.size());

        for (int i = startIndex; i < endIndex; i++) {
            var entry = entries.get(i);
            UUID uuid = entry.getKey();
            int kills = entry.getValue();
            String name = killService.getPlayerName(uuid);
            int rank = i + 1;

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            skullMeta.setDisplayName(ChatColor.GOLD + "#" + rank + " " + ChatColor.AQUA + name);
            skullMeta.setLore(List.of(
                    ChatColor.GRAY + "Kills: " + ChatColor.WHITE + kills,
                    "",
                    ChatColor.YELLOW + "Klicke für Details & Bearbeitung"
            ));
            skull.setItemMeta(skullMeta);
            inventory.setItem(i - startIndex, skull);
        }

        if (entries.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER, ChatColor.RED + "Keine Spieler im Cache",
                    List.of(ChatColor.GRAY + "Noch kein Spieler ist dem Server beigetreten.")));
        }

        // Navigation bar (row 6)
        if (safePage > 0) {
            inventory.setItem(45, createItem(Material.ARROW, ChatColor.YELLOW + "← Vorherige Seite",
                    List.of(ChatColor.GRAY + "Seite " + safePage + " anzeigen")));
        }
        inventory.setItem(49, createItem(Material.PAPER, ChatColor.WHITE + "Seite " + (safePage + 1) + "/" + totalPages,
                List.of(ChatColor.GRAY + "" + entries.size() + " Spieler insgesamt")));
        if (safePage < totalPages - 1) {
            inventory.setItem(53, createItem(Material.ARROW, ChatColor.YELLOW + "Nächste Seite →",
                    List.of(ChatColor.GRAY + "Seite " + (safePage + 2) + " anzeigen")));
        }

        return inventory;
    }

    /**
     * Builds the detail/edit view for a specific player.
     * Slot 4: Player head with total kills
     * Slots 19-25: World breakdown
     * Slots 37-43: Edit buttons
     * Slot 49: Back button
     */
    public Inventory buildDetail(Inventory inventory, KillStatsService killService, UUID playerUuid, boolean trackWorlds) {
        inventory.clear();
        String name = killService.getPlayerName(playerUuid);
        int kills = killService.getKillCache().getOrDefault(playerUuid, 0);

        // Player head with overview
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) skull.getItemMeta();
        skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUuid));
        skullMeta.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + name);
        skullMeta.setLore(List.of(
                ChatColor.GRAY + "UUID: " + ChatColor.WHITE + playerUuid.toString(),
                ChatColor.GRAY + "Kills gesamt: " + ChatColor.GOLD + kills
        ));
        skull.setItemMeta(skullMeta);
        inventory.setItem(4, skull);

        // World breakdown section
        if (trackWorlds) {
            Map<String, Integer> worlds = killService.getWorldBreakdown(playerUuid);
            if (worlds.isEmpty()) {
                inventory.setItem(22, createItem(Material.GRAY_STAINED_GLASS_PANE,
                        ChatColor.GRAY + "Keine Welt-Daten",
                        List.of(ChatColor.GRAY + "Welt-Tracking erst seit Plugin-Start")));
            } else {
                var sortedWorlds = worlds.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                        .toList();
                int[] worldSlots = {19, 20, 21, 22, 23, 24, 25};
                for (int i = 0; i < Math.min(worldSlots.length, sortedWorlds.size()); i++) {
                    var worldEntry = sortedWorlds.get(i);
                    inventory.setItem(worldSlots[i], createItem(Material.GRASS_BLOCK,
                            ChatColor.GREEN + worldEntry.getKey(),
                            List.of(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + worldEntry.getValue())));
                }
            }
        }

        // Edit buttons row
        inventory.setItem(37, createItem(Material.RED_CONCRETE, ChatColor.RED + "-10 Kills",
                List.of(ChatColor.GRAY + "Entfernt 10 Kills")));
        inventory.setItem(38, createItem(Material.RED_DYE, ChatColor.RED + "-1 Kill",
                List.of(ChatColor.GRAY + "Entfernt 1 Kill")));
        inventory.setItem(40, createItem(Material.BARRIER, ChatColor.DARK_RED + "" + ChatColor.BOLD + "Auf 0 setzen",
                List.of(ChatColor.RED + "Setzt alle Kills auf 0 zurück!")));
        inventory.setItem(42, createItem(Material.LIME_DYE, ChatColor.GREEN + "+1 Kill",
                List.of(ChatColor.GRAY + "Fügt 1 Kill hinzu")));
        inventory.setItem(43, createItem(Material.LIME_CONCRETE, ChatColor.GREEN + "+10 Kills",
                List.of(ChatColor.GRAY + "Fügt 10 Kills hinzu")));

        // Back button
        inventory.setItem(49, createItem(Material.ARROW, ChatColor.YELLOW + "Zurück",
                List.of(ChatColor.GRAY + "Zurück zur Übersicht")));

        return inventory;
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
