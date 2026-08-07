package com.example.luckprefix.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 配置增量合并工具。
 *
 * <p>以 jar 内打包的默认资源文件为基准，将用户磁盘上已存在但缺失的键补齐回去，
 * 同时保留用户对已有键的任何修改。仅新增缺失项，不覆盖、不删除已有内容。</p>
 *
 * <p>适用范围：{@code config.yml}、{@code titles.yml} 等通过 {@code saveResource} 生成的
 * 默认配置文件。不会触碰 {@code data.yml} 这种纯数据存储文件。</p>
 */
public final class ConfigUpdater {
    private ConfigUpdater() {
    }

    /**
     * 用 jar 内的默认资源合并磁盘上的用户配置，仅补齐缺失键。
     *
     * @param defaultResource jar 内资源路径（例如 {@code "config.yml"}）
     * @param userConfig      用户磁盘上已加载的配置
     * @param inputStream     jar 内默认资源的输入流，调用方负责关闭
     * @return 本次新增的键路径列表（例如 {@code ["custom-title.enabled"]}）；
     *         若无新增返回空列表
     */
    public static List<String> mergeMissing(String defaultResource, FileConfiguration userConfig, InputStream inputStream) {
        YamlConfiguration defaults;
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            defaults = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read default resource: " + defaultResource, exception);
        }

        List<String> added = new ArrayList<>();
        mergeSection(defaults, userConfig, "", added);
        return added;
    }

    /**
     * 递归合并一个配置节，把 defaults 中存在、user 中缺失的键补齐。
     *
     * <p>注意：{@code user} 始终是根配置对象，{@code fullPath} 是从根开始的完整路径。
     * 所有 set/get/contains 都使用 {@code fullPath}，避免递归切换 user 导致路径错乱。</p>
     */
    private static void mergeSection(ConfigurationSection defaults, ConfigurationSection user, String pathPrefix, List<String> added) {
        Map<String, Object> defaultValues = defaults.getValues(false);
        for (Map.Entry<String, Object> entry : defaultValues.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullPath = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;

            if (value instanceof ConfigurationSection defaultSection) {
                if (user.isConfigurationSection(fullPath)) {
                    // 两边都是 section，递归合并（user 保持根，pathPrefix 下钻）
                    mergeSection(defaultSection, user, fullPath, added);
                } else {
                    // 用户缺失整个 section，直接写入整棵子树
                    user.set(fullPath, cloneTree(defaultSection));
                    collectPaths(defaultSection, fullPath, added);
                }
            } else if (value instanceof List<?> defaultList) {
                // 列表：用户已有非空列表则保留，否则补齐默认列表
                if (!user.isList(fullPath) || user.getList(fullPath) == null || user.getList(fullPath).isEmpty()) {
                    user.set(fullPath, new ArrayList<>(defaultList));
                    added.add(fullPath);
                }
            } else {
                // 普通标量：用户缺失则补齐
                if (!user.contains(fullPath, true)) {
                    user.set(fullPath, value);
                    added.add(fullPath);
                }
            }
        }
    }

    /**
     * 深度拷贝一个配置子树为可写入的 Map 结构。
     */
    private static Map<String, Object> cloneTree(ConfigurationSection section) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection child) {
                result.put(entry.getKey(), cloneTree(child));
            } else if (value instanceof List<?> list) {
                result.put(entry.getKey(), new ArrayList<>(list));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /**
     * 收集一个 section 下所有叶子路径，用于记录新增项。
     */
    private static void collectPaths(ConfigurationSection section, String prefix, List<String> added) {
        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            String fullPath = prefix + "." + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection child) {
                collectPaths(child, fullPath, added);
            } else {
                added.add(fullPath);
            }
        }
    }
}
