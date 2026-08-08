package com.example.luckprefix.service;

import com.example.luckprefix.LuckPrefixPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/**
 * Vault 经济服务封装。
 *
 * <p>当服务器安装了 Vault 且有经济插件时可用。否则相关功能（如自定义称号收费）
 * 会根据配置决定是拒绝还是放行。</p>
 */
public final class EconomyService {
    private final LuckPrefixPlugin plugin;
    private Economy economy;
    private boolean available;

    public EconomyService(LuckPrefixPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        this.available = false;
        this.economy = null;
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            return;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            return;
        }
        RegisteredServiceProvider<Economy> provider = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return;
        }
        this.economy = provider.getProvider();
        this.available = economy != null;
        if (available) {
            plugin.getLogger().info("Vault economy hooked: " + economy.getName());
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * 返回修改自定义称号的费用。
     */
    public double customTitleCost() {
        return plugin.getConfig().getDouble("custom-title.cost", 50000.0);
    }

    /**
     * 检查玩家余额是否足够支付费用。
     */
    public boolean hasEnough(OfflinePlayer player, double amount) {
        if (!available || amount <= 0) {
            return true;
        }
        return economy.has(player, amount);
    }

    /**
     * 扣除玩家费用。返回 true 表示成功（费用为 0 或扣款成功）。
     */
    public boolean withdraw(OfflinePlayer player, double amount) {
        if (!available || amount <= 0) {
            return true;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }
}
