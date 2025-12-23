package com.poseidon.codegraph.engine.domain.parser.endpoint;

import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.engine.ScopeFilter;
import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.engine.SimpleEprEngine;
import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.loader.EprRuleLoader;
import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model.EndpointParseRule;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 端点解析服务
 * 使用 EPR 规则从源代码中解析端点
 */
@Slf4j
@Service
public class EndpointParsingService {
    
    private final EprRuleLoader ruleLoader;
    private final ScopeFilter scopeFilter;
    private final SimpleEprEngine eprEngine;
    
    private List<EndpointParseRule> allRules;
    
    public EndpointParsingService() {
        this.ruleLoader = new EprRuleLoader();
        this.scopeFilter = new ScopeFilter();
        this.eprEngine = new SimpleEprEngine();
    }
    
    @PostConstruct
    public void init() {
        // 启动时加载所有规则
        this.allRules = ruleLoader.loadAllRules();
        log.info("端点解析服务初始化完成，已加载 {} 条 EPR 规则", allRules.size());
    }
    
    /**
     * 解析文件中的端点
     *
     * @param cu 编译单元
     * @param packageName 包名
     * @param fileName 文件名
     * @param projectFilePath 项目文件路径
     * @return 解析到的端点列表
     * @deprecated 请使用 parseEndpointsForType 方法（新架构）
     */
    @Deprecated
    public List<CodeEndpoint> parseEndpoints(
            CompilationUnit cu,
            String packageName,
            String fileName,
            String projectFilePath) {
        
        if (allRules == null || allRules.isEmpty()) {
            log.debug("没有可用的 EPR 规则");
            return Collections.emptyList();
        }
        
        // 1. 根据包路径过滤规则
        log.info("开始过滤规则: 包名={}, 文件名={}, 总规则数={}", packageName, fileName, allRules.size());
        List<EndpointParseRule> applicableRules = scopeFilter.filterRules(allRules, packageName, fileName);
        
        if (applicableRules.isEmpty()) {
            log.warn("文件 {} (包: {}) 不匹配任何端点解析规则，跳过", fileName, packageName);
            return Collections.emptyList();
        }
        
        log.info("文件 {} 匹配到 {} 条规则: {}",
            fileName,
            applicableRules.size(),
            applicableRules.stream().map(EndpointParseRule::getName).collect(java.util.stream.Collectors.toList())
        );
        
        // 2. 获取类型声明
        TypeDeclaration typeDecl = getTypeDeclaration(cu);
        if (typeDecl == null) {
            log.debug("文件 {} 中没有类型声明", fileName);
            return Collections.emptyList();
        }
        
        // 3. 应用每个规则
        List<CodeEndpoint> endpoints = new ArrayList<>();
        
        for (EndpointParseRule rule : applicableRules) {
            try {
                // 对于旧的解析方法，absoluteFilePath 传 null，这样就不会触发配置扫描
                List<CodeEndpoint> parsedEndpoints = eprEngine.executeRule(rule, cu, typeDecl, projectFilePath, null);
                endpoints.addAll(parsedEndpoints);
                
                if (!parsedEndpoints.isEmpty()) {
                    log.info("规则 {} 解析到 {} 个端点", rule.getName(), parsedEndpoints.size());
                }
            } catch (Exception e) {
                log.error("执行规则 {} 失败", rule.getName(), e);
            }
        }
        
        return endpoints;
    }
    
    /**
     * 解析指定类型中的端点（用于 Processor 架构）
     * 
     * @param typeDecl 类型声明
     * @param cu 编译单元
     * @param packageName 包名
     * @param fileName 文件名
     * @param projectFilePath 项目文件路径
     * @return 解析到的端点列表
     */
    public List<CodeEndpoint> parseEndpointsForType(
            TypeDeclaration typeDecl,
            CompilationUnit cu,
            String packageName,
            String fileName,
            String projectFilePath,
            String absoluteFilePath) {
        
        if (allRules == null || allRules.isEmpty()) {
            log.debug("没有可用的 EPR 规则");
            return Collections.emptyList();
        }
        
        // 1. 根据包路径过滤规则
        List<EndpointParseRule> applicableRules = scopeFilter.filterRules(allRules, packageName, fileName);
        
        if (applicableRules.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 2. 应用每个规则
        List<CodeEndpoint> endpoints = new ArrayList<>();
        
        for (EndpointParseRule rule : applicableRules) {
            try {
                List<CodeEndpoint> parsedEndpoints = eprEngine.executeRule(rule, cu, typeDecl, projectFilePath, absoluteFilePath);
                endpoints.addAll(parsedEndpoints);
                
                if (!parsedEndpoints.isEmpty()) {
                    log.debug("规则 {} 在类 {} 中解析到 {} 个端点", 
                        rule.getName(), typeDecl.getName().getIdentifier(), parsedEndpoints.size());
                }
            } catch (Exception e) {
                log.error("执行规则 {} 失败", rule.getName(), e);
            }
        }
        
        return endpoints;
    }
    
    /**
     * 获取类型声明（类或接口）
     */
    private TypeDeclaration getTypeDeclaration(CompilationUnit cu) {
        List<?> types = cu.types();
        if (types.isEmpty()) {
            return null;
        }
        
        Object firstType = types.get(0);
        if (firstType instanceof TypeDeclaration) {
            return (TypeDeclaration) firstType;
        }
        
        return null;
    }
}

