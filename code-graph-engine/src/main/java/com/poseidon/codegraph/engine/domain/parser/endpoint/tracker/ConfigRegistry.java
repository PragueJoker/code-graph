package com.poseidon.codegraph.engine.domain.parser.endpoint.tracker;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目配置字典中心注册表
 * 管理不同项目的字典实例
 */
public class ConfigRegistry {

    private static final Map<String, ConfigDictionary> projectDictionaries = new ConcurrentHashMap<>();
    private static final ConfigScanner scanner = new ConfigScanner();

    /**
     * 获取指定项目的配置字典（如果不存在则触发扫描）
     * 
     * @param projectRoot 项目根路径
     */
    public static ConfigDictionary getDictionary(String projectRoot) {
        if (projectRoot == null || projectRoot.isEmpty()) {
            return new ConfigDictionary(); // 返回空字典
        }

        return projectDictionaries.computeIfAbsent(projectRoot, root -> {
            ConfigDictionary dict = new ConfigDictionary();
            scanner.scan(root, dict);
            return dict;
        });
    }

    /**
     * 清理某个项目的字典（用于重新扫描）
     */
    public static void invalidate(String projectRoot) {
        projectDictionaries.remove(projectRoot);
    }
}

