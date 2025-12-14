package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.engine;

import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model.EndpointParseRule;
import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model.ScopeConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 作用域过滤器
 * 根据包路径、文件名等快速过滤规则
 */
@Slf4j
public class ScopeFilter {
    
    /**
     * 过滤出适用于当前文件的规则
     *
     * @param allRules 所有规则
     * @param packageName 文件的包名，如 "com.example.controller"
     * @param fileName 文件名，如 "UserController.java"
     * @return 需要应用的规则列表
     */
    public List<EndpointParseRule> filterRules(
            List<EndpointParseRule> allRules,
            String packageName,
            String fileName) {
        
        log.info("ScopeFilter: 过滤 {} 条规则, 包名={}, 文件名={}", allRules.size(), packageName, fileName);
        
        List<EndpointParseRule> result = allRules.stream()
            .filter(rule -> {
                boolean matches = matchesScope(rule.getScope(), packageName, fileName);
                log.debug("  规则 {} 匹配结果: {}", rule.getName(), matches);
                return matches;
            })
            .collect(Collectors.toList());
        
        log.info("ScopeFilter: 过滤后剩余 {} 条规则", result.size());
        return result;
    }
    
    /**
     * 判断文件是否匹配规则的作用域
     */
    private boolean matchesScope(ScopeConfig scope, String packageName, String fileName) {
        log.info("  matchesScope: scope={}", scope);
        
        if (scope == null) {
            log.info("    scope 为 null，匹配所有文件");
            return true;  // 没有作用域限制，匹配所有文件
        }
        
        log.info("    packageIncludes={}", scope.getPackageIncludes());
        log.info("    packageExcludes={}", scope.getPackageExcludes());
        
        // 1. 检查包路径包含
        if (scope.getPackageIncludes() != null && !scope.getPackageIncludes().isEmpty()) {
            log.info("    开始检查包路径包含...");
            boolean matched = scope.getPackageIncludes().stream()
                .anyMatch(pattern -> matchesPackagePattern(packageName, pattern));
            
            log.info("    包路径包含检查结果: {}", matched);
            
            if (!matched) {
                return false;  // 不在包含列表中
            }
        }
        
        // 2. 检查包路径排除
        if (scope.getPackageExcludes() != null && !scope.getPackageExcludes().isEmpty()) {
            boolean excluded = scope.getPackageExcludes().stream()
                .anyMatch(pattern -> matchesPackagePattern(packageName, pattern));
            
            if (excluded) {
                return false;  // 在排除列表中
            }
        }
        
        // 3. 检查文件名模式（可选）
        if (scope.getFileNamePatterns() != null && !scope.getFileNamePatterns().isEmpty()) {
            boolean matched = scope.getFileNamePatterns().stream()
                .anyMatch(pattern -> matchesFileNamePattern(fileName, pattern));
            
            if (!matched) {
                return false;  // 文件名不匹配
            }
        }
        
        return true;
    }
    
    /**
     * 包路径模式匹配
     * 支持通配符：
     * - ** 匹配任意层级的包
     * - * 匹配单个包名中的任意字符
     *
     * 例如:
     * - "**.controller.**" 匹配 "com.example.controller" 和 "com.example.web.controller.admin"
     * - "com.example.*.service" 匹配 "com.example.user.service" 但不匹配 "com.example.user.admin.service"
     */
    private boolean matchesPackagePattern(String packageName, String pattern) {
        // 转换为正则表达式（注意顺序！）
        // 1. 先处理 ** → 用特殊标记替换
        String regex = pattern.replace("**", "___DOUBLE_STAR___");
        
        // 2. 再处理 . → \.
        regex = regex.replace(".", "\\.");
        
        // 3. 处理单个 *  → [^.]*
        regex = regex.replace("*", "[^.]*");
        
        // 4. 最后处理 ** → .*
        regex = regex.replace("___DOUBLE_STAR___", ".*");
        
        boolean matches = packageName.matches(regex);
        
        log.info("      包匹配: '{}' vs 模式 '{}' (regex='{}') => {}", 
            packageName, pattern, regex, matches);
        
        return matches;
    }
    
    /**
     * 文件名模式匹配
     * 支持通配符：* 匹配任意字符
     *
     * 例如:
     * - "*Controller.java" 匹配 "UserController.java"
     * - "*Client.java" 匹配 "OrderClient.java"
     */
    private boolean matchesFileNamePattern(String fileName, String pattern) {
        String regex = pattern.replace("*", ".*");
        return fileName.matches(regex);
    }
}

