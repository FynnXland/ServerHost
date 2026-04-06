package de.fynn.webserver.leaderboard;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.UUID;

public class KillGuiHolder implements InventoryHolder {

    public enum Page {
        LIST,
        DETAIL
    }

    private final Page page;
    private final int listPage;
    private final UUID selectedPlayer;

    public KillGuiHolder(Page page, int listPage, UUID selectedPlayer) {
        this.page = page;
        this.listPage = listPage;
        this.selectedPlayer = selectedPlayer;
    }

    public Page getPage() { return page; }
    public int getListPage() { return listPage; }
    public UUID getSelectedPlayer() { return selectedPlayer; }

    @Override
    public Inventory getInventory() { return null; }
}
