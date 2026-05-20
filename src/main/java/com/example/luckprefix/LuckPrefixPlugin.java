package com.example.luckprefix;

import com.example.luckprefix.command.LuckPrefixCommand;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.gui.TitleGui;
import com.example.luckprefix.listener.PlayerJoinListener;
import com.example.luckprefix.placeholder.LuckPrefixExpansion;
import com.example.luckprefix.service.LuckPermsPrefixService;
import com.example.luckprefix.title.TitleManager;
import com.example.luckprefix.util.Text;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
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
    private YamlPlayerDataStore dataStore;
    private LuckPermsPrefixService prefixService;
    private TitleGui titleGui;
    private LuckPrefixExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        this.dataStore = new YamlPlayerDataStore(this);
        this.dataStore.load();
        this.prefixService = new LuckPermsPrefixService(this, luckPerms, dataStore);
        this.titleGui = new TitleGui(this, titleManager, dataStore, prefixService);

        getServer().getPluginManager().registerEvents(titleGui, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, dataStore, titleManager, prefixService), this);

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
        LuckPrefixCommand command = new LuckPrefixCommand(this, titleManager, dataStore, prefixService, titleGui);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
            event.registrar().register("luckprefix", "Open the LuckPrefix title menu.", List.of("lpp"), command)
        );
    }

    private void registerPlaceholderExpansion() {
        if (!getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return;
        }
        this.placeholderExpansion = new LuckPrefixExpansion(this, titleManager, dataStore);
        if (placeholderExpansion.register()) {
            getLogger().info("Registered PlaceholderAPI expansion.");
        }
    }

    private void saveResourceIfMissing(String name) {
        if (!getDataFolder().toPath().resolve(name).toFile().exists()) {
            saveResource(name, false);
        }
    }
}
