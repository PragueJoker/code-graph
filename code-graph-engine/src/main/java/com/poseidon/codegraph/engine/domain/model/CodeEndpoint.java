package com.poseidon.codegraph.engine.domain.model;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 代码端点基础类（抽象）
 * 表示外部交互点：HTTP API、Kafka Topic、Redis Key、DB Table 等
 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class CodeEndpoint extends CodeNode {

    /**
     * 端点类型
     */
    private EndpointType endpointType;

    /**
     * 方向：inbound（入口）/ outbound（出口）
     */
    private String direction;

    /**
     * 是否是外部端点
     */
    private Boolean isExternal;

    /**
     * 所属服务名
     */
    private String serviceName;

    /**
     * 解析深度：full, partial, unknown
     */
    private String parseLevel;

    /**
     * 目标服务名（仅 outbound）
     */
    private String targetService;

    /**
     * 匹配标识（用于跨服务关联 MATCHES 关系）
     * 例如：HTTP: "GET /api/users/{param}"，MQ: "MQ:topic_name"
     */
    private String matchIdentity;

    /**
     * 计算端点的匹配标识（由子类实现）
     */
    public abstract String computeMatchIdentity();

    // ===== 临时属性（仅用于构建关系，不持久化） =====

    /**
     * 关联的函数对象（临时字段）
     */
    private transient CodeFunction function;

    /**
     * 获取关联的函数 ID
     */
    public String getFunctionId() {
        return function != null ? function.getId() : null;
    }

    public String getNodeType() {
        return "CodeEndpoint";
    }
}
