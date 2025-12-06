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
    UNIT_TO_FUNCTION
}

