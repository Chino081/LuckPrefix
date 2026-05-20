# LuckPrefix

LuckPrefix 是一个基于 LuckPerms Prefix 的 Paper 称号插件。玩家可以通过 GUI 查看、选择、取消自己的称号；管理员可以创建称号、给予玩家称号权限，或直接为玩家设置当前称号。

## 环境要求

- Paper `26.1.2`
- Java `25`
- LuckPerms
- PlaceholderAPI 可选

## 构建

```powershell
.\gradlew.bat clean build
```

构建后的插件文件：

```text
build/libs/LuckPrefix-1.0.0.jar
```

## 配置文件

- `config.yml`：GUI、消息、Placeholder 设置、上线同步延迟。
- `titles.yml`：称号列表。
- `data.yml`：玩家当前正在使用的称号数据。

`titles.yml` 示例：

```yaml
titles:
  vip:
    display-name: "&aVIP"
    prefix: "&a[VIP] "
    priority: 100
    material: EMERALD
    lore:
      - "&7尊贵 VIP 称号。"
    permission: "luckprefix.title.vip"
```

## 命令

- `/luckprefix`：打开自己的称号 GUI。
- `/lpp`：`/luckprefix` 的别名。
- `/luckprefix reload`：重载 `config.yml`、`titles.yml` 和 `data.yml`，并同步在线玩家。
- `/luckprefix clear`：清除自己的当前称号。
- `/luckprefix clear <player>`：清除指定玩家当前称号。
- `/luckprefix set <player> <title>`：给予玩家该称号权限，并设置为当前称号。
- `/luckprefix give <player> <title>`：只给予玩家该称号权限，不切换当前称号。
- `/luckprefix add <player> <title>`：同 `/luckprefix give`。
- `/luckprefix create <id> <displayName> <prefix> <priority> <material>`：创建称号，默认没有描述。

创建称号示例：

```text
/luckprefix create knight &bKnight &b[Knight] 150 DIAMOND_SWORD
```

该命令会写入 `titles.yml`，并自动生成权限节点：

```text
luckprefix.title.knight
```

## 权限

- `luckprefix.open`：打开称号 GUI。
- `luckprefix.reload`：重载插件配置。
- `luckprefix.admin`：管理玩家称号、创建称号、给予称号权限。
- `luckprefix.title.<id>`：使用指定称号。

## PlaceholderAPI

- `%luckprefix_current%`：当前称号 ID。
- `%luckprefix_name%`：当前称号显示名。
- `%luckprefix_prefix%`：当前称号 LuckPerms prefix。
- `%luckprefix_description%`：当前称号描述。

## 说明

LuckPrefix 设置 prefix 时只会移除插件自己之前设置过的 prefix，不会清理玩家权限组或其他插件设置的 prefix。玩家上线后会按 `data.yml` 中保存的当前称号自动同步 LuckPerms prefix。
