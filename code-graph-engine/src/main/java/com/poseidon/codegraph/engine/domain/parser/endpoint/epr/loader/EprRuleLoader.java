package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model.EndpointParseRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EPR 规则加载器
 * 从 .epr 文件加载端点解析规则
 */
@Slf4j
@Component
public class EprRuleLoader {
    
    private static final String EPR_SUFFIX = ".epr";
    private static final String BUILTIN_RULES_PATH = "classpath:endpoint-rules/builtin/*.epr";
    
    private final ResourcePatternResolver resourcePatternResolver;
    private final ObjectMapper yamlMapper;
    
    public EprRuleLoader() {
        this.resourcePatternResolver = new PathMatchingResourcePatternResolver();
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    /**
     * 加载所有 EPR 规则
     */
    public List<EndpointParseRule> loadAllRules() {
        List<EndpointParseRule> rules = new ArrayList<>();
        
        // 1. 加载内置规则
        rules.addAll(loadBuiltinRules());
        log.info("已加载内置 EPR 规则: {} 条", rules.size());
        
        // 2. 加载自定义规则（TODO: 从配置路径加载）
        
        // 3. 按优先级排序
        rules.sort(Comparator.comparingInt(EndpointParseRule::getPriority).reversed());
        
        // 4. 只保留启用的规则
        rules = rules.stream()
            .filter(rule -> rule.getEnabled() != null && rule.getEnabled())
            .collect(Collectors.toList());
        
        log.info("共加载 {} 条启用的 EPR 规则", rules.size());
        return rules;
    }
    
    /**
     * 加载内置 .epr 规则
     */
    private List<EndpointParseRule> loadBuiltinRules() {
        try {
            Resource[] resources = resourcePatternResolver.getResources(BUILTIN_RULES_PATH);
            
            log.debug("扫描内置规则: {}, 找到 {} 个文件", BUILTIN_RULES_PATH, resources.length);
            
            List<EndpointParseRule> rules = new ArrayList<>();
            for (Resource resource : resources) {
                EndpointParseRule rule = parseEprFile(resource);
                if (rule != null) {
                    rules.add(rule);
                }
            }
            
            return rules;
        } catch (IOException e) {
            log.error("加载内置 EPR 规则失败", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * 解析 .epr 文件
     */
    private EndpointParseRule parseEprFile(Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            EndpointParseRule rule = yamlMapper.readValue(is, EndpointParseRule.class);
            log.debug("成功解析规则文件: {} (name={}, priority={})", 
                resource.getFilename(), rule.getName(), rule.getPriority());
            return rule;
        } catch (Exception e) {
            log.error("解析 EPR 文件失败: {}", resource.getFilename(), e);
            return null;
        }
    }
    
    /**
     * 从文件路径加载
     */
    public EndpointParseRule loadFromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.error("EPR 文件不存在: {}", filePath);
                return null;
            }
            
            return yamlMapper.readValue(path.toFile(), EndpointParseRule.class);
        } catch (IOException e) {
            log.error("加载 EPR 文件失败: {}", filePath, e);
            return null;
        }
    }
}

