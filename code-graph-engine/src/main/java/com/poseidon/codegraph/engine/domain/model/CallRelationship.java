package com.poseidon.codegraph.engine.domain.model;

import lombok.Data;

/**
 * 调用关系
 * 表示函数之间的调用关系
 */
@Data
public class CallRelationship {
    /**
     * 关系 ID
     */
    private String id;
    
    /**
     * 调用方函数 ID
     */
    private String fromFunctionId;
    
    /**
     * 被调用方函数 ID
     */
    private String toFunctionId;
    
    /**
     * 调用位置行号
     */
    private Integer lineNumber;
    
    /**
     * 调用类型：static, virtual, direct
     */
    private String callType;
    
    /**
     * 语言
     */
    private String language;
}

