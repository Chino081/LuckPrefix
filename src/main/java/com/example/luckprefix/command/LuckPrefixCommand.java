package com.example.luckprefix.command;

import com.example.luckprefix.LuckPrefixPlugin;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.gui.TitleGui;
import com.example.luckprefix.service.LuckPermsPrefixService;
import com.example.luckprefix.service.PrefixOperationResult;
import com.example.luckprefix.title.TitleDefinition;
import com.example.luckprefix.title.TitleManager;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LuckPrefixCommand implements BasicCommand {
    private final LuckPrefixPlugin plugin;
    private final TitleManager titleManager;
    private final YamlPlayerDataStore dataStore;
    private final LuckPermsPrefixService prefixService;
    private final TitleGui titleGui;

    public LuckPrefixCommand(
        LuckPrefixPlugin plugin,
        TitleManager titleManager,
        YamlPlayerDataStore dataStore,
        LuckPermsPrefixService prefixService,
        TitleGui titleGui
    ) {
        this.plugin = plugin;
        this.titleManager = titleManager;
        this.dataStore = dataStore;
        this.prefixService = prefixService;
        this.titleGui = titleGui;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (args.length == 0) {
            openSelf(sender);
            return;
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);
        switch (subCommand) {
            case "reload" -> reload(sender);
            case "clear" -> clear(sender, args);
            case "set" -> set(sender, args);
            case "give", "add" -> give(sender, args);
            case "create" -> create(sender, args);
            case "help" -> plugin.sendUsage(sender);
            default -> plugin.sendUsage(sender);
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        List<String> suggestions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("luckprefix.open")) {
                suggestions.add("clear");
            }
            if (sender.hasPermission("luckprefix.reload")) {
                suggestions.add("reload");
            }
            if (sender.hasPermission("luckprefix.admin")) {
                suggestions.add("set");
                suggestions.add("give");
                suggestions.add("add");
                suggestions.add("create");
                suggestions.add("clear");
            }
            suggestions.add("help");
            return matching(suggestions, args[0]);
        }

        if (args.length == 2 && sender.hasPermission("luckprefix.admin") && is(args[0], "set", "give", "add", "clear")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                suggestions.add(player.getName());
            }
            return matching(suggestions, args[1]);
        }

        if (args.length == 3 && sender.hasPermission("luckprefix.admin") && is(args[0], "set", "give", "add")) {
            suggestions.addAll(titleManager.ids());
            return matching(suggestions, args[2]);
        }

        if (args.length == 6 && sender.hasPermission("luckprefix.admin") && is(args[0], "create")) {
            for (Material material : Material.values()) {
                if (material.isItem()) {
                    suggestions.add(material.name());
                }
            }
            return matching(suggestions, args[5]);
        }

        return List.of();
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return sender.hasPermission("luckprefix.open")
            || sender.hasPermission("luckprefix.reload")
            || sender.hasPermission("luckprefix.admin");
    }

    private void openSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "only-player");
            return;
        }
        if (!player.hasPermission("luckprefix.open")) {
            plugin.sendMessage(player, "no-permission");
            return;
        }
        titleGui.open(player);
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("luckprefix.reload")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }

        plugin.reloadLuckPrefix();
        for (Player player : Bukkit.getOnlinePlayers()) {
            dataStore.get(player.getUniqueId()).ifPresent(data ->
                prefixService.syncStoredTitle(player.getUniqueId(), titleManager.get(data.titleId()))
            );
        }
        plugin.sendMessage(sender, "reload-complete");
    }

    private void clear(CommandSender sender, String[] args) {
        if (args.length == 1) {
            clearSelf(sender);
            return;
        }

        if (args.length == 2) {
            clearOther(sender, args[1]);
            return;
        }

        plugin.sendUsage(sender);
    }

    private void clearSelf(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "only-player");
            return;
        }
        if (!player.hasPermission("luckprefix.open")) {
            plugin.sendMessage(player, "no-permission");
            return;
        }

        prefixService.clearTitle(player.getUniqueId()).thenAccept(result ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!result.success()) {
                    plugin.sendMessage(player, "operation-failed", Map.of("reason", result.reason()));
                    return;
                }
                plugin.sendMessage(player, "title-cleared");
            })
        );
    }

    private void clearOther(CommandSender sender, String playerName) {
        if (!sender.hasPermission("luckprefix.admin")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }

        resolveUuid(playerName).ifPresentOrElse(uuid ->
            prefixService.clearTitle(uuid).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> sendAdminClearResult(sender, playerName, result))
            ),
            () -> plugin.sendMessage(sender, "player-not-found", Map.of("player", playerName))
        );
    }

    private void set(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckprefix.admin")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }
        if (args.length != 3) {
            plugin.sendUsage(sender);
            return;
        }

        String playerName = args[1];
        String titleId = args[2];
        Optional<TitleDefinition> title = titleManager.get(titleId);
        if (title.isEmpty()) {
            plugin.sendMessage(sender, "title-not-found", Map.of("title", titleId));
            return;
        }

        resolveUuid(playerName).ifPresentOrElse(uuid ->
            prefixService.grantTitlePermission(uuid, title.get()).thenCompose(grantResult -> {
                if (!grantResult.success()) {
                    return java.util.concurrent.CompletableFuture.completedFuture(grantResult);
                }
                return prefixService.applyTitle(uuid, title.get());
            }).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> sendAdminSetResult(sender, playerName, title.get(), result))
            ),
            () -> plugin.sendMessage(sender, "player-not-found", Map.of("player", playerName))
        );
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckprefix.admin")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }
        if (args.length != 3) {
            plugin.sendUsage(sender);
            return;
        }

        String playerName = args[1];
        String titleId = args[2];
        Optional<TitleDefinition> title = titleManager.get(titleId);
        if (title.isEmpty()) {
            plugin.sendMessage(sender, "title-not-found", Map.of("title", titleId));
            return;
        }

        resolveUuid(playerName).ifPresentOrElse(uuid ->
            prefixService.grantTitlePermission(uuid, title.get()).thenAccept(result ->
                Bukkit.getScheduler().runTask(plugin, () -> sendAdminGiveResult(sender, playerName, title.get(), result))
            ),
            () -> plugin.sendMessage(sender, "player-not-found", Map.of("player", playerName))
        );
    }

    private void create(CommandSender sender, String[] args) {
        if (!sender.hasPermission("luckprefix.admin")) {
            plugin.sendMessage(sender, "no-permission");
            return;
        }
        if (args.length != 6) {
            plugin.sendUsage(sender);
            return;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_-]+")) {
            plugin.sendMessage(sender, "invalid-title-id");
            return;
        }
        if (titleManager.get(id).isPresent()) {
            plugin.sendMessage(sender, "title-already-exists", Map.of("title", id));
            return;
        }

        int priority;
        try {
            priority = Integer.parseInt(args[4]);
        } catch (NumberFormatException ignored) {
            plugin.sendMessage(sender, "invalid-priority");
            return;
        }

        Material material = Material.matchMaterial(args[5]);
        if (material == null || !material.isItem()) {
            plugin.sendMessage(sender, "invalid-material", Map.of("material", args[5]));
            return;
        }

        TitleDefinition title = new TitleDefinition(
            id,
            args[2],
            args[3],
            priority,
            material,
            List.of(),
            "luckprefix.title." + id
        );

        if (!titleManager.create(title)) {
            plugin.sendMessage(sender, "operation-failed", Map.of("reason", "Could not save titles.yml"));
            return;
        }

        plugin.sendMessage(sender, "title-created", Map.of("title", id));
    }

    private void sendAdminSetResult(CommandSender sender, String playerName, TitleDefinition title, PrefixOperationResult result) {
        if (!result.success()) {
            plugin.sendMessage(sender, "operation-failed", Map.of("reason", result.reason()));
            return;
        }
        plugin.sendMessage(sender, "admin-title-selected", Map.of("player", playerName, "title", title.displayName()));
    }

    private void sendAdminClearResult(CommandSender sender, String playerName, PrefixOperationResult result) {
        if (!result.success()) {
            plugin.sendMessage(sender, "operation-failed", Map.of("reason", result.reason()));
            return;
        }
        plugin.sendMessage(sender, "admin-title-cleared", Map.of("player", playerName));
    }

    private void sendAdminGiveResult(CommandSender sender, String playerName, TitleDefinition title, PrefixOperationResult result) {
        if (!result.success()) {
            plugin.sendMessage(sender, "operation-failed", Map.of("reason", result.reason()));
            return;
        }
        plugin.sendMessage(sender, "admin-title-granted", Map.of("player", playerName, "title", title.displayName()));
    }

    private Optional<UUID> resolveUuid(String playerName) {
        Player online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return Optional.of(online.getUniqueId());
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline()) {
            return Optional.empty();
        }
        return Optional.of(offlinePlayer.getUniqueId());
    }

    private boolean is(String value, String... expected) {
        for (String candidate : expected) {
            if (candidate.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private Collection<String> matching(Collection<String> values, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream()
            .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized))
            .distinct()
            .sorted()
            .toList();
    }
}
