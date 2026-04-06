package de.fynn.webserver.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class StatsGuiHolder implements InventoryHolder {

    public enum Page {
        DASHBOARD,
        TRAFFIC,
        SYSTEM,
        SECURITY
    }

    private final Page page;

    public StatsGuiHolder(Page page) {
        this.page = page;
    }

    public Page getPage() {
        return page;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
