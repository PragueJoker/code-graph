package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model;

import lombok.Data;
import java.util.Map;

/**
 * 端点构建配置
 */
@Data
public class BuildConfig {
    
    // 必需字段
    private String endpointType;  // HTTP, KAFKA, REDIS, DB
    private String direction;      // inbound, outbound
    private Boolean isExternal;
    
    // HTTP
    private String httpMethod;
    private String path;
    private String normalizedPath;
    
    // Kafka
    private String topic;
    private String operation;
    
    // Redis
    private String keyPattern;
    private String dataStructure;
    
    // DB
    private String tableName;
    
    // 通用
    private String serviceName;
    private String parseLevel;
    private String endpointId;
    
    // 关系
    private RelationshipConfig relationship;
    
    @Data
    public static class RelationshipConfig {
        private String type;
        private String direction;
        private Map<String, Object> metadata;
    }
}

