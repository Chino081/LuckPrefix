package com.example.luckprefix.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {
    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.legacyAmpersand();

    private Text() {
    }

    public static Component component(String input) {
        return AMPERSAND.deserialize(input == null ? "" : input);
    }

}
