package com.example.luckprefix.title;

import com.example.luckprefix.LuckPrefixPlugin;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * 评估玩家是否满足称号的解锁条件。
 *
 * <p>条件字符串支持 PlaceholderAPI 变量（例如 {@code %luckperms_meta_level%}），
 * 解析后通过简单的比较运算符（{@code >= <= > < == !=}）进行求值。
 * 空条件视为不通过；权限节点仍然作为默认的解锁方式。</p>
 */
public final class TitleUnlockService {
    private static final Pattern COMPARISON = Pattern.compile(
        "^\\s*(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$"
    );

    private final LuckPrefixPlugin plugin;

    public TitleUnlockService(LuckPrefixPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 判断玩家是否已解锁指定称号。
     *
     * <p>当称号配置了解锁条件且 PlaceholderAPI 可用时，先解析条件中的 PAPI 变量再求值；
     * 否则回退到权限节点判定。</p>
     */
    public boolean isUnlocked(Player player, TitleDefinition title) {
        if (player.hasPermission(title.permission())) {
            return true;
        }
        String condition = title.unlockCondition();
        if (condition == null || condition.isBlank()) {
            return false;
        }
        if (!plugin.isPlaceholderApiAvailable()) {
            return false;
        }
        return evaluate(player, condition);
    }

    private boolean evaluate(Player player, String raw) {
        String resolved = PlaceholderAPI.setPlaceholders(player, raw);
        Matcher matcher = COMPARISON.matcher(resolved);
        if (!matcher.matches()) {
            // 解析后没有可识别的运算符，视为不通过。
            return false;
        }
        String left = matcher.group(1).trim();
        String operator = matcher.group(2);
        String right = matcher.group(3).trim();

        Double leftNumber = tryParseDouble(left);
        Double rightNumber = tryParseDouble(right);
        if (leftNumber != null && rightNumber != null) {
            return compareNumbers(leftNumber, rightNumber, operator);
        }
        return compareStrings(left, right, operator);
    }

    private boolean compareNumbers(double left, double right, String operator) {
        return switch (operator) {
            case ">=" -> left >= right;
            case "<=" -> left <= right;
            case ">" -> left > right;
            case "<" -> left < right;
            case "==" -> Double.compare(left, right) == 0;
            case "!=" -> Double.compare(left, right) != 0;
            default -> false;
        };
    }

    private boolean compareStrings(String left, String right, String operator) {
        return switch (operator) {
            case "==" -> left.equalsIgnoreCase(right);
            case "!=" -> !left.equalsIgnoreCase(right);
            case ">=" -> left.compareToIgnoreCase(right) >= 0;
            case "<=" -> left.compareToIgnoreCase(right) <= 0;
            case ">" -> left.compareToIgnoreCase(right) > 0;
            case "<" -> left.compareToIgnoreCase(right) < 0;
            default -> false;
        };
    }

    private Double tryParseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public Optional<String> describeFailure(TitleDefinition title) {
        if (title.unlockCondition() == null || title.unlockCondition().isBlank()) {
            return Optional.empty();
        }
        return Optional.of(title.unlockCondition());
    }
}
