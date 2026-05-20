# LuckPrefix

LuckPrefix is a Paper plugin built around LuckPerms prefixes. Players can view, select, and clear their own titles from a GUI. Admins can create titles, grant title permissions to players, or directly set a player's current title.

## Requirements

- Paper `26.1.2`
- Java `25`
- LuckPerms
- PlaceholderAPI optional

## Build

```powershell
.\gradlew.bat clean build
```

The plugin jar will be generated at:

```text
build/libs/LuckPrefix-1.0.0.jar
```

## Configuration Files

- `config.yml`: GUI, messages, PlaceholderAPI settings, and join sync delay.
- `titles.yml`: title definitions.
- `data.yml`: saved player current-title data.

Example `titles.yml`:

```yaml
titles:
  vip:
    display-name: "&aVIP"
    prefix: "&a[VIP] "
    priority: 100
    material: EMERALD
    lore:
      - "&7Premium VIP title."
    permission: "luckprefix.title.vip"
```

## Commands

- `/luckprefix`: open your title GUI.
- `/lpp`: alias for `/luckprefix`.
- `/luckprefix reload`: reload `config.yml`, `titles.yml`, and `data.yml`, then sync online players.
- `/luckprefix clear`: clear your current title.
- `/luckprefix clear <player>`: clear another player's current title.
- `/luckprefix set <player> <title>`: grant the title permission and set it as the player's current title.
- `/luckprefix give <player> <title>`: grant the title permission only, without switching the player's current title.
- `/luckprefix add <player> <title>`: same as `/luckprefix give`.
- `/luckprefix create <id> <displayName> <prefix> <priority> <material>`: create a title with an empty description by default.

Create title example:

```text
/luckprefix create knight &bKnight &b[Knight] 150 DIAMOND_SWORD
```

This writes the title to `titles.yml` and automatically uses this permission node:

```text
luckprefix.title.knight
```

## Permissions

- `luckprefix.open`: open the title GUI.
- `luckprefix.reload`: reload plugin configuration.
- `luckprefix.admin`: manage player titles, create titles, and grant title permissions.
- `luckprefix.title.<id>`: use a specific title.

## PlaceholderAPI

- `%luckprefix_current%`: current title ID.
- `%luckprefix_name%`: current title display name.
- `%luckprefix_prefix%`: current title LuckPerms prefix.
- `%luckprefix_description%`: current title description.

## Notes

When LuckPrefix applies a prefix, it only removes the prefix previously applied by LuckPrefix. It does not remove prefixes from permission groups or other plugins. When a player joins, LuckPrefix syncs their LuckPerms prefix from the title saved in `data.yml`.
