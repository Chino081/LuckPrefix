package com.example.luckprefix.data;

import com.example.luckprefix.LuckPrefixPlugin;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class YamlPlayerDataStore {
    private final LuckPrefixPlugin plugin;
    private final File file;
    private final Map<UUID, PlayerTitleData> cache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerCustomTitle> customCache = new ConcurrentHashMap<>();
    private YamlConfiguration yaml;

    public YamlPlayerDataStore(LuckPrefixPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public synchronized void load() {
        cache.clear();
        customCache.clear();
        this.yaml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection players = yaml.getConfigurationSection("players");
        if (players == null) {
            yaml.createSection("players");
            save();
            return;
        }

        for (String key : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String path = "players." + key;
                String titleId = yaml.getString(path + ".title");
                String prefix = yaml.getString(path + ".prefix", "");
                int priority = yaml.getInt(path + ".priority", 0);
                if (titleId != null && !titleId.isBlank()) {
                    cache.put(uuid, new PlayerTitleData(titleId, prefix, priority));
                }

                String customContent = yaml.getString(path + ".custom-title");
                String customPrefix = yaml.getString(path + ".custom-prefix", "");
                if (customContent != null && !customContent.isBlank()) {
                    customCache.put(uuid, new PlayerCustomTitle(customContent, customPrefix));
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Ignoring invalid UUID in data.yml: " + key);
            }
        }
    }

    public Optional<PlayerTitleData> get(UUID uuid) {
        return Optional.ofNullable(cache.get(uuid));
    }

    public Optional<PlayerCustomTitle> getCustom(UUID uuid) {
        return Optional.ofNullable(customCache.get(uuid));
    }

    public synchronized void set(UUID uuid, PlayerTitleData data) {
        cache.put(uuid, data);
        String path = "players." + uuid;
        yaml.set(path + ".title", data.titleId());
        yaml.set(path + ".prefix", data.appliedPrefix());
        yaml.set(path + ".priority", data.appliedPriority());
        save();
    }

    public synchronized void setCustom(UUID uuid, PlayerCustomTitle data) {
        customCache.put(uuid, data);
        String path = "players." + uuid;
        yaml.set(path + ".custom-title", data.content());
        yaml.set(path + ".custom-prefix", data.prefix());
        save();
    }

    public synchronized void clearCustom(UUID uuid) {
        customCache.remove(uuid);
        String path = "players." + uuid;
        yaml.set(path + ".custom-title", null);
        yaml.set(path + ".custom-prefix", null);
        save();
    }

    public synchronized void clear(UUID uuid) {
        cache.remove(uuid);
        customCache.remove(uuid);
        yaml.set("players." + uuid, null);
        save();
    }

    public synchronized void save() {
        if (yaml == null) {
            yaml = new YamlConfiguration();
            yaml.createSection("players");
        }
        try {
            yaml.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save data.yml: " + exception.getMessage());
        }
    }
}
