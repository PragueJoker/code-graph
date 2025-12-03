package com.poseidon.codegraph.engine.application.model;

import lombok.Data;

/**
 * 调用关系数据对象（应用层）
 */
@Data
public class CallRelationshipDO {
    private String id;
    private String fromFunctionId;
    private String toFunctionId;
    private Integer lineNumber;
    private String callType;
    private String language;
}

