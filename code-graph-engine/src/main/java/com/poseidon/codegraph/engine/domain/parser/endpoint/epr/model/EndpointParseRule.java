package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * EPR 规则定义
 * 完整的端点解析规则
 */
@Data
public class EndpointParseRule {
    
    // ===== 元信息 =====
    private String name;
    private String description;
    private String version = "1.0";
    private Boolean enabled = true;
    private Integer priority = 100;
    
    /**
     * 规则类型：http-inbound, http-outbound, kafka-producer, kafka-consumer, redis-access, db-access
     * 如果指定了 type，可以省略 build 配置，使用默认配置
     */
    private String type;
    
    // ===== 作用域 =====
    private ScopeConfig scope;
    
    // ===== 上下文追踪 =====
    private List<ContextTrackConfig> context;
    
    // ===== 节点定位 =====
    private LocateConfig locate;
    
    // ===== 信息提取 =====
    private Map<String, ExtractConfig> extract;
    
    // ===== 端点构建（可选，如果有 type 则可以省略） =====
    private BuildConfig build;
}

