package com.poseidon.codegraph.engine.domain.model;

/**
 * 关系类型枚举
 * 每个关系类型定义了源节点和目标节点的标签，用于 Neo4j 查询和关系创建
 */
public enum RelationshipType {
    /**
     * 函数调用关系
     */
    CALLS("CodeFunction", "CodeFunction"),
    
    /**
     * 包包含单元
     */
    PACKAGE_TO_UNIT("CodePackage", "CodeUnit"),
    
    /**
     * 单元包含函数
     */
    UNIT_TO_FUNCTION("CodeUnit", "CodeFunction"),
    
    /**
     * 端点到函数（入站端点，如 HTTP 请求进入某个 Controller 方法）
     */
    ENDPOINT_TO_FUNCTION("CodeEndpoint", "CodeFunction"),
    
    /**
     * 函数到端点（出站端点，如函数调用外部 API）
     */
    FUNCTION_TO_ENDPOINT("CodeFunction", "CodeEndpoint");
    
    /**
     * 源节点的 Neo4j 标签
     */
    private final String fromLabel;
    
    /**
     * 目标节点的 Neo4j 标签
     */
    private final String toLabel;
    
    RelationshipType(String fromLabel, String toLabel) {
        this.fromLabel = fromLabel;
        this.toLabel = toLabel;
    }
    
    public String getFromLabel() {
        return fromLabel;
    }
    
    public String getToLabel() {
        return toLabel;
    }
}

