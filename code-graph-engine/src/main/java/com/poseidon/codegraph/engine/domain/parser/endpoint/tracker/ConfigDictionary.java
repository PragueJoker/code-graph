package com.poseidon.codegraph.engine.domain.parser.endpoint.tracker;

import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 全局配置字典
 * 存储 Key 到多个 ConfigValue 的映射，并提供占位符展开算法
 */
@Slf4j
public class ConfigDictionary {

    // 核心存储：Key -> [所有可能的值]
    private final Map<String, Set<ConfigValue>> dictionary = new ConcurrentHashMap<>();
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    /**
     * 添加一个配置项
     */
    public void addProperty(String key, String value, String sourceFile, String profile, int priority) {
        dictionary.computeIfAbsent(key, k -> new HashSet<>())
                  .add(new ConfigValue(value, sourceFile, profile, priority));
    }

    /**
     * 获取一个 Key 的所有可能值
     */
    public Set<String> getValues(String key) {
        Set<ConfigValue> values = dictionary.get(key);
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        return values.stream().map(ConfigValue::getValue).collect(Collectors.toSet());
    }

    /**
     * 解析文本中的占位符，返回所有可能的最终字符串（笛卡尔积展开）
     * 例如：文本是 "${host}:${port}"，host 有 2 个值，port 有 2 个值，将返回 4 个组合结果
     */
    public List<String> resolveAll(String text) {
        if (text == null || !text.contains("${")) {
            return Collections.singletonList(text);
        }

        List<String> results = new ArrayList<>();
        results.add(text);

        // 循环直到没有任何占位符可以被解析
        int maxDepth = 5; 
        while (maxDepth-- > 0) {
            List<String> nextBatch = new ArrayList<>();
            boolean anyResolved = false;

            for (String currentText : results) {
                Matcher matcher = PLACEHOLDER_PATTERN.matcher(currentText);
                if (matcher.find()) {
                    String fullMatch = matcher.group(0); // ${key:default}
                    String keyPart = matcher.group(1);   // key:default
                    
                    String key = keyPart;
                    String defaultValue = null;
                    if (keyPart.contains(":")) {
                        int colonIndex = keyPart.indexOf(':');
                        key = keyPart.substring(0, colonIndex);
                        defaultValue = keyPart.substring(colonIndex + 1);
                    }

                    Set<String> possibleValues = getValues(key);
                    
                    // 如果字典里没找到，且没有默认值，保留原样
                    if (possibleValues.isEmpty()) {
                        if (defaultValue != null) {
                            nextBatch.add(currentText.replace(fullMatch, defaultValue));
                            anyResolved = true;
                        } else {
                            nextBatch.add(currentText); // 保持原样，等待下次可能被其他 key 触发
                        }
                    } else {
                        // 笛卡尔积展开
                        for (String val : possibleValues) {
                            nextBatch.add(currentText.replace(fullMatch, val));
                        }
                        anyResolved = true;
                    }
                } else {
                    nextBatch.add(currentText);
                }
            }

            results = nextBatch.stream().distinct().collect(Collectors.toList());
            if (!anyResolved) break;
        }

        return results;
    }

    public boolean isEmpty() {
        return dictionary.isEmpty();
    }

    public int size() {
        return dictionary.size();
    }
}

