package com.example.luckprefix.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {
    /**
     * 同时支持传统 & 代码和 RGB hex 格式（&#RRGGBB）的序列化器。
     *hex 格式会被转换为 Adventure 原生支持的 &#RRGGBB 形式。
     */
    private static final LegacyComponentSerializer AMPERSAND =
        LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private Text() {
    }

    public static Component component(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        return AMPERSAND.deserialize(preprocessHex(input));
    }

    /**
     * 将 {@code &#RRGGBB} 形式的 RGB 颜色转换为本插件内部统一使用的格式。
     * {@link LegacyComponentSerializer} 配合 {@code hexColors()} 已原生支持
     * {@code &#RRGGBB}，但此处保留入口便于后续扩展（例如带 & 前缀的裸 hex）。
     */
    public static String preprocessHex(String input) {
        if (input == null) {
            return "";
        }
        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, "&#" + matcher.group(1).toLowerCase());
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * 去除所有颜色代码（传统 & 代码、&  前缀 hex、Bukkit &x&r&r&g&g&b&b 格式），
     * 返回纯文本，用于长度计算。
     */
    public static String stripColor(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        String result = input;
        // &#RRGGBB / &#rrggbb
        result = result.replaceAll("(?i)&#[0-9a-f]{6}", "");
        // Bukkit RGB 格式 &x&r&r&g&g&b&b
        result = result.replaceAll("(?i)&x(&[0-9a-f]){6}", "");
        // 传统 & 代码
        result = result.replaceAll("(?i)&[0-9a-fk-or]", "");
        // section 符号变体
        result = result.replaceAll("\u00a7[0-9a-fk-or]", "");
        return result;
    }

    /**
     * 判断输入是否包含任意颜色代码（传统或 RGB）。
     */
    public static boolean hasColor(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        if (HEX_PATTERN.matcher(input).find()) {
            return true;
        }
        if (input.matches("(?i).*&x(&[0-9a-f]){6}.*")) {
            return true;
        }
        return input.matches("(?i).*&[0-9a-fk-or].*");
    }

    /**
     * 提取字符串开头的所有连续颜色代码（传统 & 代码、&#RRGGBB、Bukkit &x... 格式）。
     *
     * <p>例如 {@code "&b无敌"} 返回 {@code "&b"}，
     * {@code "&#FF5555&l无敌"} 返回 {@code "&#FF5555&l"}，
     * {@code "无敌"} 返回空字符串。</p>
     */
    public static String extractLeadingColor(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int index = 0;
        while (index < input.length()) {
            if (index + 1 < input.length() && input.charAt(index) == '&') {
                // 检查 &#RRGGBB
                if (index + 7 < input.length() && input.charAt(index + 1) == '#') {
                    String hex = input.substring(index, index + 8);
                    if (hex.matches("(?i)&#[0-9a-f]{6}")) {
                        builder.append(hex);
                        index += 8;
                        continue;
                    }
                }
                // 检查 Bukkit &x&r&r&g&g&b&b
                if (index + 13 < input.length() && input.charAt(index + 1) == 'x') {
                    String bukkit = input.substring(index, index + 14);
                    if (bukkit.matches("(?i)&x(&[0-9a-f]){6}")) {
                        builder.append(bukkit);
                        index += 14;
                        continue;
                    }
                }
                // 检查传统 & 代码
                char code = input.charAt(index + 1);
                if (String.valueOf(code).matches("(?i)[0-9a-fk-or]")) {
                    builder.append(input, index, index + 2);
                    index += 2;
                    continue;
                }
            }
            break;
        }
        return builder.toString();
    }
}
