package com.example.luckprefix.title;

import java.util.List;
import org.bukkit.Material;

public record TitleDefinition(
    String id,
    String displayName,
    String prefix,
    int priority,
    Material material,
    List<String> lore,
    String permission
) {
}
