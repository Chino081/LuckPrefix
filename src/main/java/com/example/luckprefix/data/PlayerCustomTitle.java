package com.example.luckprefix.data;

/**
 * 玩家自定义称号数据。
 *
 * @param content 玩家输入的原始内容，可包含颜色代码（例如 {@code &b无敌}）。
 * @param prefix  最终拼装后的前缀字符串（例如 {@code &b[无敌] }），用于写入 LuckPerms。
 */
public record PlayerCustomTitle(String content, String prefix) {
}
