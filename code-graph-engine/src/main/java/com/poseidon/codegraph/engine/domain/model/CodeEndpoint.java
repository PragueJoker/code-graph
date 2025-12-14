package com.poseidon.codegraph.engine.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码端点节点
 * 表示外部交互点：HTTP API、Kafka Topic、Redis Key、DB Table 等
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeEndpoint extends CodeNode {
    
    /**
     * 端点类型：HTTP, KAFKA, REDIS, DB
     */
    private String endpointType;
    
    /**
     * 方向：inbound（入口）/ outbound（出口）
     */
    private String direction;
    
    /**
     * 是否是外部端点
     */
    private Boolean isExternal;
    
    // ===== HTTP 相关属性 =====
    private String httpMethod;        // GET, POST, PUT, DELETE 等
    private String path;               // /api/users/{id}
    private String normalizedPath;     // 标准化后的路径
    
    // ===== Kafka 相关属性 =====
    private String topic;              // user-events
    private String operation;          // PRODUCE, CONSUME
    
    // ===== Redis 相关属性 =====
    private String keyPattern;         // user:${id}:profile
    private String dataStructure;      // STRING, HASH, LIST, SET
    
    // ===== DB 相关属性 =====
    private String tableName;          // t_user
    private String dbOperation;        // SELECT, INSERT, UPDATE, DELETE
    
    // ===== 通用属性 =====
    private String serviceName;        // 所属服务名
    private String parseLevel;         // full, partial, unknown
    private String targetService;      // 目标服务名（仅 outbound）
    
    // ===== 关联属性 =====
    private String functionId;         // 关联的函数ID
    
    public String getNodeType() {
        return "CodeEndpoint";
    }
}

