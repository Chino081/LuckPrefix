package com.example.luckprefix.title;

import com.example.luckprefix.LuckPrefixPlugin;
import com.example.luckprefix.data.PlayerCustomTitle;
import com.example.luckprefix.data.YamlPlayerDataStore;
import com.example.luckprefix.service.LuckPermsPrefixService;
import com.example.luckprefix.service.PrefixOperationResult;
import com.example.luckprefix.util.Text;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Material;

/**
 * 管理玩家自定义称号的创建、校验、应用与清除。
 *
 * <p>自定义称号由玩家通过命令设置文本内容（可包含颜色代码），
 * 系统自动拼装为 {@code &f[内容]} 形式的 prefix 写入 LuckPerms。</p>
 */
public final class CustomTitleService {
    /** 自定义称号的固定 ID，用于在数据存储和 GUI 中标识。 */
    public static final String CUSTOM_ID = "custom";

    private final LuckPrefixPlugin plugin;
    private final YamlPlayerDataStore dataStore;
    private final LuckPermsPrefixService prefixService;

    public CustomTitleService(LuckPrefixPlugin plugin, YamlPlayerDataStore dataStore, LuckPermsPrefixService prefixService) {
        this.plugin = plugin;
        this.dataStore = dataStore;
        this.prefixService = prefixService;
    }

    public boolean isEnabled() {
        return plugin.getConfig().getBoolean("custom-title.enabled", true);
    }

    public int maxLength() {
        return Math.max(1, plugin.getConfig().getInt("custom-title.max-length", 5));
    }

    /** 允许的字符：中文、英文大小写、数字。颜色代码由 Text.stripColor 统一剥离后再校验。 */
    private static final java.util.regex.Pattern ALLOWED_CHARS =
        java.util.regex.Pattern.compile("^[\\u4e00-\\u9fff\\u3400-\\u4dbf\\u3000-\\u303f\\uff00-\\uffef0-9a-zA-Z]+$");

    /**
     * 校验玩家输入的自定义称号内容，成功返回 {@link ValidationResult#ok()}，
     * 失败返回携带消息路径和参数的结果。
     *
     * <p>长度计算会去除所有颜色代码，按字符宽度统计：
     * 中文/全角字符每个算 2，英文/数字/半角字符每个算 1。
     * {@code max-length} 表示允许的最大中文数量，实际宽度上限为 {@code max-length * 2}。</p>
     *
     * <p>仅允许中文、英文字母、数字。禁止输入 {@code [ ] _ - . / \ : ; , ! ? @ # $ % ^ & * ( ) + = | < > ~ ` " '}
     * 等任何特殊符号。颜色代码（{@code &x}、{@code &#RRGGBB}）会先剥离再校验，不算特殊符号。</p>
     */
    public ValidationResult validate(String content) {
        if (content == null || content.isBlank()) {
            return ValidationResult.fail("custom-empty");
        }
        String stripped = Text.stripColor(content).trim();
        if (stripped.isEmpty()) {
            return ValidationResult.fail("custom-only-color");
        }
        // 校验纯文本是否只含允许的字符（中文/英文/数字）
        if (!ALLOWED_CHARS.matcher(stripped).matches()) {
            return ValidationResult.fail("custom-invalid-chars");
        }
        // 屏蔽词检查（大小写不敏感）
        String blocked = findBlockedWord(stripped);
        if (blocked != null) {
            return ValidationResult.fail("custom-blocked", Map.of("word", blocked));
        }
        int limit = maxLength();
        int maxWidth = limit * 2;
        int width = textWidth(stripped);
        if (width > maxWidth) {
            return ValidationResult.fail("custom-too-long",
                Map.of("limit", String.valueOf(limit), "max", String.valueOf(maxWidth)));
        }
        return ValidationResult.ok();
    }

    /**
     * 检查纯文本是否包含配置的屏蔽词（大小写不敏感）。
     *
     * @return 命中的屏蔽词原文，未命中返回 null
     */
    private String findBlockedWord(String text) {
        List<String> words = plugin.getConfig().getStringList("custom-title.blocked-words");
        if (words == null || words.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String word : words) {
            if (word == null || word.isBlank()) {
                continue;
            }
            if (lower.contains(word.toLowerCase(java.util.Locale.ROOT))) {
                return word;
            }
        }
        return null;
    }

    /**
     * 计算文本宽度：中文/全角字符每个算 2，其余算 1。
     */
    private int textWidth(String input) {
        int width = 0;
        for (int index = 0; index < input.length(); index++) {
            char ch = input.charAt(index);
            width += isWide(ch) ? 2 : 1;
        }
        return width;
    }

    /**
     * 判断字符是否为宽字符（中文、全角标点等）。
     */
    private boolean isWide(char ch) {
        if (ch >= 0x4E00 && ch <= 0x9FFF) {
            return true; // CJK 统一汉字
        }
        if (ch >= 0x3400 && ch <= 0x4DBF) {
            return true; // CJK 扩展 A
        }
        if (ch >= 0x3000 && ch <= 0x303F) {
            return true; // CJK 标点
        }
        if (ch >= 0xFF00 && ch <= 0xFFEF) {
            return true; // 全角字符
        }
        return false;
    }

    /**
     * 拼装自定义称号的 prefix。
     *
     * <p>提取玩家内容中开头的颜色代码放到方括号之前，确保颜色对方括号和内容同时生效。
     * 若无颜色代码则使用默认白色 &f。自动包裹方括号并在末尾追加空格。</p>
     */
    public String buildPrefix(String content) {
        String trimmed = content.trim();
        String leadingColor = Text.extractLeadingColor(trimmed);
        String body = leadingColor.isEmpty() ? trimmed : trimmed.substring(leadingColor.length());
        String colorPrefix = leadingColor.isEmpty() ? "&f" : leadingColor;
        return colorPrefix + "[" + body + "]";
    }

    /**
     * 设置玩家的自定义称号内容（仅写入数据存储，不应用 LuckPerms）。
     */
    public PlayerCustomTitle setContent(UUID uuid, String content) {
        String prefix = buildPrefix(content);
        PlayerCustomTitle data = new PlayerCustomTitle(content.trim(), prefix);
        dataStore.setCustom(uuid, data);
        return data;
    }

    /**
     * 将玩家当前的自定义称号应用到 LuckPerms。
     */
    public CompletableFuture<PrefixOperationResult> applyCustom(UUID uuid) {
        Optional<PlayerCustomTitle> data = dataStore.getCustom(uuid);
        if (data.isEmpty()) {
            return CompletableFuture.completedFuture(PrefixOperationResult.fail("未设置自定义称号"));
        }
        return prefixService.applyTitle(uuid, buildDefinition(data.get()));
    }

    /**
     * 清除玩家的自定义称号内容与 LuckPerms 前缀。
     */
    public CompletableFuture<PrefixOperationResult> clearCustom(UUID uuid) {
        return prefixService.clearTitle(uuid).thenApply(result -> {
            if (result.success()) {
                dataStore.clearCustom(uuid);
            }
            return result;
        });
    }

    /**
     * 构造自定义称号对应的动态 TitleDefinition。
     */
    public TitleDefinition buildDefinition(PlayerCustomTitle data) {
        String displayName = data.content();
        String prefix = data.prefix();
        int priority = plugin.getConfig().getInt("custom-title.priority", 50);
        String materialName = plugin.getConfig().getString("custom-title.material", "WRITABLE_BOOK");
        Material material = Material.matchMaterial(materialName);
        if (material == null || !material.isItem()) {
            material = Material.WRITABLE_BOOK;
        }
        String permission = "luckprefix.custom";
        return new TitleDefinition(
            CUSTOM_ID,
            displayName,
            prefix,
            priority,
            material,
            List.of(),
            permission,
            ""
        );
    }

}
