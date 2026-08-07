package com.example.luckprefix;

import com.example.luckprefix.command.LuckPrefixCommand;
import com.example.luckprefix.config.ConfigUpdater;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.gui.TitleGui;
import com.example.luckprefix.listener.PlayerJoinListener;
import com.example.luckprefix.placeholder.LuckPrefixExpansion;
import com.example.luckprefix.service.LuckPermsPrefixService;
import com.example.luckprefix.title.CustomTitleService;
import com.example.luckprefix.title.TitleManager;
import com.example.luckprefix.title.TitleUnlockService;
import com.example.luckprefix.util.Text;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.luckperms.api.LuckPerms;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckPrefixPlugin extends JavaPlugin {
    private LuckPerms luckPerms;
    private TitleManager titleManager;
    private TitleUnlockService unlockService;
    private CustomTitleService customTitleService;
    private YamlPlayerDataStore dataStore;
    private LuckPermsPrefixService prefixService;
    private TitleGui titleGui;
    private LuckPrefixExpansion placeholderExpansion;
    private boolean placeholderApiAvailable;

    @Override
    public void onEnable() {
        setupConfig();
        saveResourceIfMissing("titles.yml");
        saveResourceIfMissing("data.yml");

        RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider == null) {
            getLogger().severe("LuckPerms is required but was not found.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.luckPerms = provider.getProvider();
        this.titleManager = new TitleManager(this);
        this.titleManager.reload();
        this.unlockService = new TitleUnlockService(this);
        this.dataStore = new YamlPlayerDataStore(this);
        this.dataStore.load();
        this.prefixService = new LuckPermsPrefixService(this, luckPerms, dataStore);
        this.customTitleService = new CustomTitleService(this, dataStore, prefixService);
        this.titleGui = new TitleGui(this, titleManager, dataStore, prefixService, unlockService, customTitleService);

        getServer().getPluginManager().registerEvents(titleGui, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, dataStore, titleManager, prefixService, customTitleService), this);

        registerCommand();
        registerPlaceholderExpansion();
        getLogger().info("LuckPrefix enabled with " + titleManager.all().size() + " titles.");
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        if (dataStore != null) {
            dataStore.save();
        }
    }

    public void reloadLuckPrefix() {
        reloadConfig();
        titleManager.reload();
        dataStore.load();
    }

    public void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, Map.of());
    }

    public void sendMessage(CommandSender sender, String path, Map<String, String> replacements) {
        String message = getConfig().getString("messages." + path, path);
        sender.sendMessage(formatMessage(message, replacements));
    }

    public void sendUsage(CommandSender sender) {
        List<String> lines = getConfig().getStringList("messages.usage");
        if (lines.isEmpty()) {
            sender.sendMessage(Component.text("/luckprefix"));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(formatMessage(line, Map.of()));
        }
    }

    public Component formatMessage(String input, Map<String, String> replacements) {
        Map<String, String> merged = new HashMap<>(replacements);
        merged.putIfAbsent("prefix", getConfig().getString("messages.prefix", ""));

        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            output = output.replace("%" + entry.getKey() + "%", entry.getValue() == null ? "" : entry.getValue());
        }
        return Text.component(output);
    }

    public TitleManager titleManager() {
        return titleManager;
    }

    public TitleUnlockService unlockService() {
        return unlockService;
    }

    public CustomTitleService customTitleService() {
        return customTitleService;
    }

    public YamlPlayerDataStore dataStore() {
        return dataStore;
    }

    public LuckPermsPrefixService prefixService() {
        return prefixService;
    }

    public TitleGui titleGui() {
        return titleGui;
    }

    private void registerCommand() {
        LuckPrefixCommand command = new LuckPrefixCommand(this, titleManager, dataStore, prefixService, titleGui, customTitleService);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            event.registrar().register("luckprefix", "Open the LuckPrefix title menu.", List.of("lpp"), command)
        );
    }

    private void registerPlaceholderExpansion() {
        this.placeholderApiAvailable = getServer().getPluginManager().isPluginEnabled("PlaceholderAPI");
        if (!placeholderApiAvailable) {
            return;
        }
        this.placeholderExpansion = new LuckPrefixExpansion(this, titleManager, dataStore, customTitleService);
        if (placeholderExpansion.register()) {
            getLogger().info("Registered PlaceholderAPI expansion.");
        }
    }

    public boolean isPlaceholderApiAvailable() {
        return placeholderApiAvailable;
    }

    private void saveResourceIfMissing(String name) {
        if (!getDataFolder().toPath().resolve(name).toFile().exists()) {
            saveResource(name, false);
        }
    }

    /**
     * 初始化并增量合并 config.yml。
     *
     * <p>首次运行：生成默认配置。已存在：将 jar 内默认配置中缺失的新键
     * 补齐到用户文件，不覆盖用户已有修改。</p>
     */
    private void setupConfig() {
        saveDefaultConfig();
        List<String> added = mergeMissingFromResource("config.yml", getConfig());
        if (!added.isEmpty()) {
            try {
                getConfig().save(new java.io.File(getDataFolder(), "config.yml"));
                getLogger().info("Merged " + added.size() + " new config key(s) into config.yml: " + String.join(", ", added));
                reloadConfig();
            } catch (IOException exception) {
                getLogger().warning("Could not save merged config.yml: " + exception.getMessage());
            }
        }
    }

    /**
     * 用 jar 内的默认资源合并用户磁盘配置，返回本次新增的键路径列表。
     */
    private List<String> mergeMissingFromResource(String resource, org.bukkit.configuration.file.FileConfiguration userConfig) {
        try (InputStream input = getResource(resource)) {
            if (input == null) {
                return List.of();
            }
            return ConfigUpdater.mergeMissing(resource, userConfig, input);
        } catch (IOException exception) {
            getLogger().warning("Could not read default resource '" + resource + "': " + exception.getMessage());
            return List.of();
        }
    }
}
