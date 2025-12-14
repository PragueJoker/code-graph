package com.poseidon.codegraph.engine.domain.model;

/**
 * 关系类型枚举
 */
public enum RelationshipType {
    /**
     * 函数调用关系
     */
    CALLS,
    
    /**
     * 包包含单元
     */
    PACKAGE_TO_UNIT,
    
    /**
     * 单元包含函数
     */
    UNIT_TO_FUNCTION,
    
    /**
     * 端点到函数（入站端点，如 HTTP 请求进入某个 Controller 方法）
     */
    ENDPOINT_TO_FUNCTION,
    
    /**
     * 函数到端点（出站端点，如函数调用外部 API）
     */
    FUNCTION_TO_ENDPOINT
}

