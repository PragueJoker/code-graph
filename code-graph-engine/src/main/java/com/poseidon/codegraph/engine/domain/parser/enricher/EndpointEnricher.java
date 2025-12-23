package com.poseidon.codegraph.engine.domain.parser.enricher;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import com.poseidon.codegraph.engine.domain.model.CodeFunction;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import com.poseidon.codegraph.engine.domain.model.CodeRelationship;
import com.poseidon.codegraph.engine.domain.model.CodeUnit;
import com.poseidon.codegraph.engine.domain.model.RelationshipType;
import com.poseidon.codegraph.engine.domain.parser.endpoint.EndpointParsingService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.util.List;
import java.util.UUID;

/**
 * 端点增强器
 * 使用 EPR 规则解析代码中的端点（HTTP、Kafka、Redis、DB 等）
 * 
 * @deprecated 请使用新的 Processor 架构：
 *             - com.poseidon.codegraph.engine.domain.parser.processor.EndpointProcessor
 *             - com.poseidon.codegraph.engine.domain.parser.ProcessorRegistry
 */
@Deprecated
@Slf4j
public class EndpointEnricher implements GraphEnricher {
    
    private final EndpointParsingService endpointParsingService;
    
    public EndpointEnricher(EndpointParsingService endpointParsingService) {
        this.endpointParsingService = endpointParsingService;
    }
    
    @Override
    public void enrich(CodeGraph graph, CompilationUnit cu, CodeGraphContext context) {
        log.warn("EndpointEnricher 已废弃，请使用 EndpointProcessor。此方法不再执行任何操作。");
        // 新架构使用 EndpointProcessor 替代，请参考：
        // - com.poseidon.codegraph.engine.domain.parser.processor.EndpointProcessor
        // - com.poseidon.codegraph.engine.domain.parser.ProcessorRegistry
        return;
        
        /* 以下代码已废弃
        log.debug("开始端点增强: file={}", context.getProjectFilePath());
        
        try {
            // 1. 解析端点
            List<CodeEndpoint> endpoints = endpointParsingService.parseEndpoints(
                cu,
                context.getPackageName() != null ? context.getPackageName() : "",
                extractFileName(context.getProjectFilePath()),
                context.getProjectFilePath()
            );
        */
    }
    
    /**
     * 从 CodeGraph 中查找指定的 CodeFunction 对象
     * 通过 endpoint 中临时存储的 functionId（qualifiedName）来查找
     */
    private CodeFunction findFunctionInGraph(CodeGraph graph, CodeEndpoint endpoint) {
        String functionId = endpoint.getFunctionId();
        if (functionId == null || functionId.isEmpty()) {
            return null;
        }
        
        // 遍历所有 units 中的 functions，找到匹配的 qualifiedName
        for (CodeUnit unit : graph.getUnitsAsList()) {
            for (CodeFunction function : unit.getFunctions()) {
                if (functionId.equals(function.getId()) || 
                    functionId.equals(function.getQualifiedName())) {
                    return function;
                }
            }
        }
        
        log.warn("在 CodeGraph 中未找到 functionId={}  的 CodeFunction", functionId);
        return null;
    }
    
    /**
     * 构建端点与函数的关系
     * 
     * @deprecated 此方法已废弃，新架构使用 EndpointProcessor 在 AST 遍历过程中直接构建关系
     */
    @Deprecated
    private void buildEndpointRelationships(CodeGraph graph, List<CodeEndpoint> endpoints, 
                                           CodeGraphContext context) {
        for (CodeEndpoint endpoint : endpoints) {
            // 获取端点关联的 function
            CodeFunction function = endpoint.getFunction();
            if (function != null) {
                CodeRelationship rel = new CodeRelationship();
                rel.setId(UUID.randomUUID().toString());
                
                // 根据端点方向设置关系类型和方向
                if ("inbound".equals(endpoint.getDirection())) {
                    // inbound: Endpoint -> Function
                    rel.setRelationshipType(RelationshipType.ENDPOINT_TO_FUNCTION);
                    rel.setFromNodeId(endpoint.getId());
                    rel.setToNodeId(function.getId());
                } else if ("outbound".equals(endpoint.getDirection())) {
                    // outbound: Function -> Endpoint
                    rel.setRelationshipType(RelationshipType.FUNCTION_TO_ENDPOINT);
                    rel.setFromNodeId(function.getId());
                    rel.setToNodeId(endpoint.getId());
                }
                
                rel.setLanguage("java");
                graph.addRelationship(rel);
                
                log.info("✓ 构建端点关系: {} -[{}]-> {}", 
                    function.getQualifiedName(), rel.getRelationshipType(), endpoint.getName());
            } else {
                log.warn("✗ 端点 function 为空，跳过关系创建: name={}", endpoint.getName());
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

