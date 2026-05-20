package com.example.luckprefix.listener;

import com.example.luckprefix.LuckPrefixPlugin;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.service.LuckPermsPrefixService;
import com.example.luckprefix.title.TitleManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class PlayerJoinListener implements Listener {
    private final LuckPrefixPlugin plugin;
    private final YamlPlayerDataStore dataStore;
    private final TitleManager titleManager;
    private final LuckPermsPrefixService prefixService;

    public PlayerJoinListener(
        LuckPrefixPlugin plugin,
        YamlPlayerDataStore dataStore,
        TitleManager titleManager,
        LuckPermsPrefixService prefixService
    ) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.titleManager = titleManager;
        this.prefixService = prefixService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        int delay = Math.max(0, plugin.getConfig().getInt("sync-on-join-delay-ticks", 20));
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            dataStore.get(player.getUniqueId()).ifPresent(data ->
                prefixService.syncStoredTitle(player.getUniqueId(), titleManager.get(data.titleId()))
            ), delay
        );
    }
}
