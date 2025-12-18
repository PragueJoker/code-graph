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
    
    // ===== 临时属性（仅用于构建关系，不持久化） =====
    
    /**
     * 从 AST 提取的函数 ID（临时字段）
     * EPR 引擎设置此字段，EndpointProcessor 用它查找 CodeFunction 对象
     */
    private transient String functionId;
    
    /**
     * 关联的函数对象（临时字段，不持久化）
     * EndpointProcessor 设置此字段，用于构建关系
     * 注意：一个 Endpoint 实例对应一个调用点，所以只关联单个 Function
     */
    private transient CodeFunction function;
    
    public String getNodeType() {
        return "CodeEndpoint";
    }
}

