package com.example.luckprefix.title;

import com.example.luckprefix.LuckPrefixPlugin;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class TitleManager {
    private final LuckPrefixPlugin plugin;
    private final Map<String, TitleDefinition> titles = new LinkedHashMap<>();
    private final File file;

    public TitleManager(LuckPrefixPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "titles.yml");
    }

    public void reload() {
        titles.clear();

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("titles");
        if (section == null) {
            plugin.getLogger().warning("No titles section found in titles.yml.");
            return;
        }

        List<TitleDefinition> parsed = new ArrayList<>();
        for (String rawId : section.getKeys(false)) {
            String id = normalizeId(rawId);
            ConfigurationSection titleSection = section.getConfigurationSection(rawId);
            if (titleSection == null) {
                continue;
            }

            String displayName = titleSection.getString("display-name", rawId);
            String prefix = titleSection.getString("prefix", "");
            int priority = titleSection.getInt("priority", 0);
            String materialName = titleSection.getString("material", "NAME_TAG");
            Material material = Material.matchMaterial(materialName == null ? "NAME_TAG" : materialName);
            if (material == null || !material.isItem()) {
                plugin.getLogger().warning("Invalid material for title '" + rawId + "': " + materialName + ". Using NAME_TAG.");
                material = Material.NAME_TAG;
            }

            List<String> lore = titleSection.getStringList("lore");
            String permission = titleSection.getString("permission", "luckprefix.title." + id);
            String unlockCondition = titleSection.getString("unlock-condition", "");
            if (unlockCondition == null) {
                unlockCondition = "";
            }
            parsed.add(new TitleDefinition(id, displayName, prefix, priority, material, List.copyOf(lore), permission, unlockCondition));
        }

        parsed.stream()
            .sorted(Comparator.comparingInt(TitleDefinition::priority).reversed().thenComparing(TitleDefinition::id))
            .forEach(title -> titles.put(title.id(), title));
    }

    public Optional<TitleDefinition> get(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(titles.get(normalizeId(id)));
    }

    public Collection<TitleDefinition> all() {
        return List.copyOf(titles.values());
    }

    public List<String> ids() {
        return List.copyOf(titles.keySet());
    }

    public boolean create(TitleDefinition title) {
        if (titles.containsKey(normalizeId(title.id()))) {
            return false;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String path = "titles." + normalizeId(title.id());
        config.set(path + ".display-name", title.displayName());
        config.set(path + ".prefix", title.prefix());
        config.set(path + ".priority", title.priority());
        config.set(path + ".material", title.material().name());
        config.set(path + ".lore", title.lore());
        config.set(path + ".permission", title.permission());
        if (title.unlockCondition() != null && !title.unlockCondition().isBlank()) {
            config.set(path + ".unlock-condition", title.unlockCondition());
        }

        try {
            config.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save titles.yml: " + exception.getMessage());
            return false;
        }

        reload();
        return true;
    }

    private String normalizeId(String id) {
        return id.toLowerCase(Locale.ROOT);
    }
}
