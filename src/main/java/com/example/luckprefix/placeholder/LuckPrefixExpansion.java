package com.example.luckprefix.placeholder;

import com.example.luckprefix.LuckPrefixPlugin;
import com.example.luckprefix.data.PlayerCustomTitle;
import com.example.luckprefix.data.PlayerTitleData;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.title.CustomTitleService;
import com.example.luckprefix.title.TitleDefinition;
import com.example.luckprefix.title.TitleManager;
import java.util.Locale;
import java.util.Optional;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LuckPrefixExpansion extends PlaceholderExpansion {
    private final LuckPrefixPlugin plugin;
    private final TitleManager titleManager;
    private final YamlPlayerDataStore dataStore;
    private final CustomTitleService customTitleService;

    public LuckPrefixExpansion(
        LuckPrefixPlugin plugin,
        TitleManager titleManager,
        YamlPlayerDataStore dataStore,
        CustomTitleService customTitleService
    ) {
        this.plugin = plugin;
        this.titleManager = titleManager;
        this.dataStore = dataStore;
        this.customTitleService = customTitleService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "luckprefix";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return none();
        }

        Optional<PlayerTitleData> data = dataStore.get(player.getUniqueId());
        boolean isCustom = data.isPresent()
            && CustomTitleService.CUSTOM_ID.equalsIgnoreCase(data.get().titleId());
        Optional<TitleDefinition> title = isCustom
            ? dataStore.getCustom(player.getUniqueId()).map(customTitleService::buildDefinition)
            : data.flatMap(stored -> titleManager.get(stored.titleId()));
        String key = params.toLowerCase(Locale.ROOT);

        return switch (key) {
            case "current" -> data.map(PlayerTitleData::titleId).orElse(none());
            case "name" -> title.map(TitleDefinition::displayName).orElse(none());
            case "prefix" -> title.map(TitleDefinition::prefix).orElse(none());
            case "description" -> title.map(this::description).orElse(none());
            case "custom" -> dataStore.getCustom(player.getUniqueId())
                .map(PlayerCustomTitle::content)
                .orElse(none());
            default -> null;
        };
    }

    private String description(TitleDefinition title) {
        String separator = plugin.getConfig().getString("placeholders.description-separator", " ");
        return String.join(separator, title.lore());
    }

    private String none() {
        return plugin.getConfig().getString("placeholders.none", "");
    }
}
