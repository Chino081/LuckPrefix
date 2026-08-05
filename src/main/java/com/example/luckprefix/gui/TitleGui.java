package com.example.luckprefix.gui;

import com.example.luckprefix.LuckPrefixPlugin;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.service.LuckPermsPrefixService;
import com.example.luckprefix.service.PrefixOperationResult;
import com.example.luckprefix.title.TitleDefinition;
import com.example.luckprefix.title.TitleManager;
import com.example.luckprefix.title.TitleUnlockService;
import com.example.luckprefix.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class TitleGui implements Listener {
    private static final String ACTION_CLEAR = "clear";
    private static final String ACTION_NEXT = "next";
    private static final String ACTION_PREVIOUS = "previous";

    private final LuckPrefixPlugin plugin;
    private final TitleManager titleManager;
    private final YamlPlayerDataStore dataStore;
    private final LuckPermsPrefixService prefixService;
    private final TitleUnlockService unlockService;
    private final NamespacedKey titleKey;
    private final NamespacedKey actionKey;

    public TitleGui(
        LuckPrefixPlugin plugin,
        TitleManager titleManager,
        YamlPlayerDataStore dataStore,
        LuckPermsPrefixService prefixService,
        TitleUnlockService unlockService
    ) {
        this.plugin = plugin;
        this.titleManager = titleManager;
        this.dataStore = dataStore;
        this.prefixService = prefixService;
        this.unlockService = unlockService;
        this.titleKey = new NamespacedKey(plugin, "title_id");
        this.actionKey = new NamespacedKey(plugin, "action");
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int requestedPage) {
        List<TitleDefinition> titles = new ArrayList<>(titleManager.all());
        List<Integer> slots = titleSlots();
        int pageSize = Math.max(1, slots.size());
        int maxPage = Math.max(0, (titles.size() - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        int size = inventorySize();

        TitleMenuHolder holder = new TitleMenuHolder(page);
        Inventory inventory = Bukkit.createInventory(holder, size, Text.component(plugin.getConfig().getString("gui.title", "&8Titles")));
        holder.inventory(inventory);

        if (plugin.getConfig().getBoolean("gui.fill-empty-slots", true)) {
            fill(inventory);
        }

        String selectedId = dataStore.get(player.getUniqueId()).map(data -> data.titleId()).orElse("");
        int start = page * pageSize;
        for (int index = 0; index < pageSize && start + index < titles.size(); index++) {
            int slot = slots.get(index);
            if (slot < 0 || slot >= size) {
                continue;
            }
            TitleDefinition title = titles.get(start + index);
            inventory.setItem(slot, titleItem(player, title, title.id().equalsIgnoreCase(selectedId)));
        }

        if (plugin.getConfig().getBoolean("gui.clear-button.enabled", true)) {
            setConfiguredActionItem(inventory, "gui.clear-button", ACTION_CLEAR, Material.BARRIER);
        }
        if (maxPage > 0 && page > 0) {
            setConfiguredActionItem(inventory, "gui.previous-page", ACTION_PREVIOUS, Material.ARROW);
        }
        if (maxPage > 0 && page < maxPage) {
            setConfiguredActionItem(inventory, "gui.next-page", ACTION_NEXT, Material.ARROW);
        }

        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof TitleMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String action = meta.getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action != null) {
            handleAction(player, holder.page(), action);
            return;
        }

        String titleId = meta.getPersistentDataContainer().get(titleKey, PersistentDataType.STRING);
        if (titleId == null) {
            return;
        }

        Optional<TitleDefinition> title = titleManager.get(titleId);
        if (title.isEmpty()) {
            plugin.sendMessage(player, "title-not-found", Map.of("title", titleId));
            open(player, holder.page());
            return;
        }

        selectTitle(player, title.get());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof TitleMenuHolder) {
            event.setCancelled(true);
        }
    }

    private void selectTitle(Player player, TitleDefinition title) {
        if (!unlockService.isUnlocked(player, title)) {
            plugin.sendMessage(player, "title-no-permission");
            return;
        }

        prefixService.applyTitle(player.getUniqueId(), title).thenAccept(result ->
            Bukkit.getScheduler().runTask(plugin, () -> handleSelectionResult(player, title, result))
        );
    }

    private void handleSelectionResult(Player player, TitleDefinition title, PrefixOperationResult result) {
        if (!player.isOnline()) {
            return;
        }
        if (!result.success()) {
            plugin.sendMessage(player, "operation-failed", Map.of("reason", result.reason()));
            return;
        }
        player.closeInventory();
        plugin.sendMessage(player, "title-selected", Map.of("title", title.displayName()));
    }

    private void handleAction(Player player, int page, String action) {
        switch (action) {
            case ACTION_CLEAR -> prefixService.clearTitle(player.getUniqueId()).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (!result.success()) {
                        plugin.sendMessage(player, "operation-failed", Map.of("reason", result.reason()));
                        return;
                    }
                    player.closeInventory();
                    plugin.sendMessage(player, "title-cleared");
                })
            );
            case ACTION_NEXT -> open(player, page + 1);
            case ACTION_PREVIOUS -> open(player, page - 1);
            default -> {
            }
        }
    }

    private ItemStack titleItem(Player player, TitleDefinition title, boolean selected) {
        ItemStack item = new ItemStack(title.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.component(title.displayName()));

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        for (String line : title.lore()) {
            lore.add(Text.component(line));
        }
        if (!title.lore().isEmpty()) {
            lore.add(net.kyori.adventure.text.Component.empty());
        }

        String statusPath;
        if (!unlockService.isUnlocked(player, title)) {
            statusPath = "status-lore.locked";
            String condition = title.unlockCondition();
            if (condition != null && !condition.isBlank()) {
                String conditionLore = plugin.getConfig().getString("status-lore.locked-condition", "");
                if (!conditionLore.isBlank()) {
                    lore.add(Text.component(conditionLore.replace("%condition%", condition)));
                }
            }
        } else if (selected) {
            statusPath = "status-lore.selected";
            if (plugin.getConfig().getBoolean("gui.selected-glow", true)) {
                meta.setEnchantmentGlintOverride(true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        } else {
            statusPath = "status-lore.available";
        }
        lore.add(Text.component(plugin.getConfig().getString(statusPath, "")));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(titleKey, PersistentDataType.STRING, title.id());
        item.setItemMeta(meta);
        return item;
    }

    private void setConfiguredActionItem(Inventory inventory, String path, String action, Material fallback) {
        int slot = plugin.getConfig().getInt(path + ".slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        Material material = Material.matchMaterial(plugin.getConfig().getString(path + ".material", fallback.name()));
        if (material == null || !material.isItem()) {
            material = fallback;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Text.component(plugin.getConfig().getString(path + ".name", action)));
        List<net.kyori.adventure.text.Component> lore = plugin.getConfig().getStringList(path + ".lore").stream()
            .map(Text::component)
            .toList();
        meta.lore(lore);
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
    }

    private void fill(Inventory inventory) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("gui.filler-material", "BLACK_STAINED_GLASS_PANE"));
        if (material == null || !material.isItem()) {
            material = Material.BLACK_STAINED_GLASS_PANE;
        }
        ItemStack filler = new ItemStack(material);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.empty());
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private int inventorySize() {
        int configured = plugin.getConfig().getInt("gui.size", 27);
        int clamped = Math.max(9, Math.min(54, configured));
        return ((clamped + 8) / 9) * 9;
    }

    private List<Integer> titleSlots() {
        List<Integer> slots = plugin.getConfig().getIntegerList("gui.title-slots");
        if (!slots.isEmpty()) {
            return slots;
        }
        int size = inventorySize();
        List<Integer> fallback = new ArrayList<>();
        for (int slot = 0; slot < size; slot++) {
            fallback.add(slot);
        }
        return fallback;
    }
}
