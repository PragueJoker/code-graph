package com.poseidon.codegraph.engine.domain.parser.endpoint.tracker;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置文件自动扫描器
 * 按照白名单规则在项目目录中寻找并解析配置文件
 */
@Slf4j
public class ConfigScanner {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private static final Pattern PROFILE_PATTERN = Pattern.compile("application-([^.]+)\\.(?:yml|yaml|properties)");

    /**
     * 自动扫描项目根目录下的所有配置文件
     */
    public void scan(String projectRoot, ConfigDictionary dictionary) {
        if (projectRoot == null || projectRoot.isEmpty()) return;

        Path rootPath = Paths.get(projectRoot);
        if (!Files.exists(rootPath)) return;

        log.info("开始扫描项目配置文件: {}", projectRoot);

        // 扫描范围：src/main/resources 和根目录下的 config 目录
        String[] searchPaths = {"src/main/resources", "config", "src/main/resources/config"};

        for (String subPath : searchPaths) {
            Path searchDir = rootPath.resolve(subPath);
            if (Files.exists(searchDir) && Files.isDirectory(searchDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(searchDir)) {
                    for (Path file : stream) {
                        String fileName = file.getFileName().toString();
                        if (isTargetConfigFile(fileName)) {
                            parseAndFill(file, rootPath, dictionary);
                        }
                    }
                } catch (IOException e) {
                    log.error("扫描目录失败: {}", searchDir, e);
                }
            }
        }
    }

    private boolean isTargetConfigFile(String fileName) {
        return fileName.equals("application.yml") || fileName.equals("application.yaml") ||
               fileName.equals("application.properties") || fileName.equals("bootstrap.yml") ||
               fileName.equals("bootstrap.yaml") || fileName.equals("bootstrap.properties") ||
               fileName.startsWith("application-");
    }

    private void parseAndFill(Path filePath, Path rootPath, ConfigDictionary dictionary) {
        String fileName = filePath.getFileName().toString();
        String relativePath = rootPath.relativize(filePath).toString();
        
        // 推断 Profile
        String profile = "default";
        Matcher matcher = PROFILE_PATTERN.matcher(fileName);
        if (matcher.find()) {
            profile = matcher.group(1);
        }
        int priority = profile.equals("default") ? 1 : 10;

        try {
            if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                JsonNode root = yamlMapper.readTree(filePath.toFile());
                flattenYaml("", root, relativePath, profile, priority, dictionary);
            } else if (fileName.endsWith(".properties")) {
                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(filePath.toFile())) {
                    props.load(fis);
                    for (String key : props.stringPropertyNames()) {
                        dictionary.addProperty(key, props.getProperty(key), relativePath, profile, priority);
                    }
                }
            }
            log.debug("成功解析配置文件: {} (profile: {})", relativePath, profile);
        } catch (Exception e) {
            log.warn("解析配置文件失败: {}, error: {}", relativePath, e.getMessage());
        }
    }

    private void flattenYaml(String prefix, JsonNode node, String source, String profile, int priority, ConfigDictionary dictionary) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                flattenYaml(prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey(), 
                            entry.getValue(), source, profile, priority, dictionary);
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                flattenYaml(prefix + "[" + i + "]", node.get(i), source, profile, priority, dictionary);
            }
        } else if (node.isValueNode()) {
            dictionary.addProperty(prefix, node.asText(), source, profile, priority);
        }
    }
}

