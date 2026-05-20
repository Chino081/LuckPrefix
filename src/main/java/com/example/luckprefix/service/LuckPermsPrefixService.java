package com.example.luckprefix.service;

import com.example.luckprefix.LuckPrefixPlugin;
import com.example.luckprefix.data.PlayerTitleData;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.title.TitleDefinition;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;

public final class LuckPermsPrefixService {
    private final LuckPrefixPlugin plugin;
    private final LuckPerms luckPerms;
    private final YamlPlayerDataStore dataStore;

    public LuckPermsPrefixService(LuckPrefixPlugin plugin, LuckPerms luckPerms, YamlPlayerDataStore dataStore) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.dataStore = dataStore;
    }

    public CompletableFuture<PrefixOperationResult> applyTitle(UUID uuid, TitleDefinition title) {
        return luckPerms.getUserManager().loadUser(uuid)
            .thenCompose(user -> {
                Optional<PlayerTitleData> oldData = dataStore.get(uuid);
                oldData.ifPresent(data -> removeStoredPrefix(user, data));

                PrefixNode newNode = PrefixNode.builder(title.prefix(), title.priority()).build();
                user.data().add(newNode);

                return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> {
                    dataStore.set(uuid, new PlayerTitleData(title.id(), title.prefix(), title.priority()));
                    return PrefixOperationResult.ok();
                });
            })
            .exceptionally(exception -> {
                plugin.getLogger().warning("Could not apply title '" + title.id() + "' for " + uuid + ": " + exception.getMessage());
                return PrefixOperationResult.fail(exception.getMessage());
            });
    }

    public CompletableFuture<PrefixOperationResult> clearTitle(UUID uuid) {
        return luckPerms.getUserManager().loadUser(uuid)
            .thenCompose(user -> {
                dataStore.get(uuid).ifPresent(data -> removeStoredPrefix(user, data));
                return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> {
                    dataStore.clear(uuid);
                    return PrefixOperationResult.ok();
                });
            })
            .exceptionally(exception -> {
                plugin.getLogger().warning("Could not clear title for " + uuid + ": " + exception.getMessage());
                return PrefixOperationResult.fail(exception.getMessage());
            });
    }

    public CompletableFuture<PrefixOperationResult> grantTitlePermission(UUID uuid, TitleDefinition title) {
        return luckPerms.getUserManager().loadUser(uuid)
            .thenCompose(user -> {
                PermissionNode permissionNode = PermissionNode.builder(title.permission()).build();
                user.data().add(permissionNode);
                return luckPerms.getUserManager().saveUser(user).thenApply(ignored -> PrefixOperationResult.ok());
            })
            .exceptionally(exception -> {
                plugin.getLogger().warning("Could not grant title permission '" + title.permission() + "' for " + uuid + ": " + exception.getMessage());
                return PrefixOperationResult.fail(exception.getMessage());
            });
    }

    public CompletableFuture<PrefixOperationResult> syncStoredTitle(UUID uuid, Optional<TitleDefinition> currentTitle) {
        Optional<PlayerTitleData> data = dataStore.get(uuid);
        if (data.isEmpty()) {
            return CompletableFuture.completedFuture(PrefixOperationResult.ok());
        }

        if (currentTitle.isEmpty()) {
            return clearTitle(uuid);
        }

        return applyTitle(uuid, currentTitle.get());
    }

    private void removeStoredPrefix(User user, PlayerTitleData data) {
        if (data.appliedPrefix() == null || data.appliedPrefix().isBlank()) {
            return;
        }
        PrefixNode oldNode = PrefixNode.builder(data.appliedPrefix(), data.appliedPriority()).build();
        user.data().remove(oldNode);
    }
}
