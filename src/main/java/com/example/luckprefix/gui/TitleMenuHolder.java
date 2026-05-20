package com.example.luckprefix.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class TitleMenuHolder implements InventoryHolder {
    private Inventory inventory;
    private final int page;

    public TitleMenuHolder(int page) {
        this.page = page;
    }

    public int page() {
        return page;
    }

    void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
