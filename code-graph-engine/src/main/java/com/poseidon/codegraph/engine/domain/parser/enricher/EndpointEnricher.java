package com.poseidon.codegraph.engine.domain.parser.enricher;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import com.poseidon.codegraph.engine.domain.model.CodeRelationship;
import com.poseidon.codegraph.engine.domain.model.RelationshipType;
import com.poseidon.codegraph.engine.domain.parser.endpoint.EndpointParsingService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.List;
import java.util.UUID;

/**
 * 端点增强器
 * 使用 EPR 规则解析代码中的端点（HTTP、Kafka、Redis、DB 等）
 */
@Slf4j
public class EndpointEnricher implements GraphEnricher {
    
    private final EndpointParsingService endpointParsingService;
    
    public EndpointEnricher(EndpointParsingService endpointParsingService) {
        this.endpointParsingService = endpointParsingService;
    }
    
    @Override
    public void enrich(CodeGraph graph, CompilationUnit cu, CodeGraphContext context) {
        log.debug("开始端点增强: file={}", context.getProjectFilePath());
        
        try {
            // 1. 解析端点
            List<CodeEndpoint> endpoints = endpointParsingService.parseEndpoints(
                cu,
                context.getPackageName() != null ? context.getPackageName() : "",
                extractFileName(context.getProjectFilePath()),
                context.getProjectFilePath()
            );
            
            if (endpoints.isEmpty()) {
                log.debug("文件中未发现端点: file={}", context.getProjectFilePath());
                return;
            }
            
            // 2. 添加端点到 CodeGraph
            for (CodeEndpoint endpoint : endpoints) {
                // 设置 Git 信息
                endpoint.setGitRepoUrl(context.getGitRepoUrl());
                endpoint.setGitBranch(context.getGitBranch());
                
                graph.addEndpoint(endpoint);
                
                log.info("添加端点: type={}, path={}, function={}", 
                    endpoint.getEndpointType(), 
                    endpoint.getPath(), 
                    endpoint.getFunctionId());
            }
            
            // 3. 构建端点与函数的关系
            buildEndpointRelationships(graph, endpoints, context);
            
            log.info("端点增强完成: file={}, endpointCount={}", 
                context.getProjectFilePath(), endpoints.size());
            
        } catch (Exception e) {
            log.error("端点增强失败: file={}, error={}", 
                context.getProjectFilePath(), e.getMessage(), e);
            // 不抛出异常，允许解析继续
        }
    }
    
    /**
     * 构建端点与函数的关系
     */
    private void buildEndpointRelationships(CodeGraph graph, List<CodeEndpoint> endpoints, 
                                           CodeGraphContext context) {
        for (CodeEndpoint endpoint : endpoints) {
            // 端点已经包含 functionId，创建关系
            if (endpoint.getFunctionId() != null && !endpoint.getFunctionId().isEmpty()) {
                CodeRelationship rel = new CodeRelationship();
                rel.setId(UUID.randomUUID().toString());
                rel.setFromNodeId(endpoint.getId());
                rel.setToNodeId(endpoint.getFunctionId());
                
                // 根据端点方向设置关系类型
                if ("inbound".equals(endpoint.getDirection())) {
                    rel.setRelationshipType(RelationshipType.ENDPOINT_TO_FUNCTION);
                } else if ("outbound".equals(endpoint.getDirection())) {
                    rel.setRelationshipType(RelationshipType.FUNCTION_TO_ENDPOINT);
                }
                
                rel.setLanguage("java");
                graph.addRelationship(rel);
                
                log.info("✓ 构建端点关系: {} -[{}]-> {}", 
                    endpoint.getId(), rel.getRelationshipType(), endpoint.getFunctionId());
            } else {
                log.warn("✗ 端点 functionId 为空，跳过关系创建: path={}", endpoint.getPath());
            }
        }
        log.info("端点关系构建完成，共处理 {} 个端点", endpoints.size());
    }
    
    /**
     * 从路径中提取文件名
     */
    private String extractFileName(String projectFilePath) {
        if (projectFilePath == null) {
            return "";
        }
        int lastSlash = Math.max(projectFilePath.lastIndexOf('/'), projectFilePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? projectFilePath.substring(lastSlash + 1) : projectFilePath;
    }
    
    @Override
    public String getName() {
        return "EndpointEnricher";
    }
    
    @Override
    public int getPriority() {
        // 端点解析在基础解析之后，优先级设为 200
        return 200;
    }
}

