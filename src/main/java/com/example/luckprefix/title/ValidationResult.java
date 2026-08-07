package com.example.luckprefix.title;

import java.util.Map;

/**
 * 自定义称号校验结果。
 *
 * <p>校验通过时 {@link #success} 为 true；失败时携带消息路径和占位符替换参数，
 * 由调用方通过 {@code plugin.sendMessage} 发送给玩家。</p>
 */
public record ValidationResult(boolean success, String messagePath, Map<String, String> replacements) {
    public static ValidationResult ok() {
        return new ValidationResult(true, "", Map.of());
    }

    public static ValidationResult fail(String messagePath) {
        return new ValidationResult(false, messagePath, Map.of());
    }

    public static ValidationResult fail(String messagePath, Map<String, String> replacements) {
        return new ValidationResult(false, messagePath, replacements);
    }
}
