package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.engine;

import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model.BuildConfig;
import lombok.extern.slf4j.Slf4j;


/**
 * 默认 BuildConfig 工厂
 * 根据规则类型生成默认的端点构建配置
 */
@Slf4j
public class DefaultBuildConfigFactory {
    
    /**
     * 根据规则类型创建默认的 BuildConfig
     */
    public static BuildConfig createDefault(String type) {
        if (type == null) {
            return null;
        }
        
        BuildConfig config = new BuildConfig();
        BuildConfig.RelationshipConfig relationship = new BuildConfig.RelationshipConfig();
        
        switch (type.toLowerCase()) {
            case "http-inbound":
                config.setEndpointType("HTTP");
                config.setDirection("inbound");
                config.setIsExternal(false);
                config.setHttpMethod("${httpMethod}");
                config.setPath("${path}");
                relationship.setType("ROUTES_TO");
                relationship.setDirection("endpoint -> function");
                config.setRelationship(relationship);
                break;
                
            case "http-outbound":
                config.setEndpointType("HTTP");
                config.setDirection("outbound");
                config.setIsExternal(true);
                config.setHttpMethod("${httpMethod}");
                config.setPath("${path}");
                config.setParseLevel("auto");
                relationship.setType("INVOKES_REMOTE");
                relationship.setDirection("function -> endpoint");
                config.setRelationship(relationship);
                break;
                
            case "kafka-producer":
                config.setEndpointType("KAFKA");
                config.setDirection("outbound");
                config.setIsExternal(true);
                config.setTopic("${topic}");
                config.setOperation("PRODUCE");
                relationship.setType("PRODUCES");
                relationship.setDirection("function -> endpoint");
                config.setRelationship(relationship);
                break;
                
            case "kafka-consumer":
                config.setEndpointType("KAFKA");
                config.setDirection("inbound");
                config.setIsExternal(false);
                config.setTopic("${topic}");
                config.setOperation("CONSUME");
                relationship.setType("CONSUMES");
                relationship.setDirection("function -> endpoint");
                config.setRelationship(relationship);
                break;
                
            case "redis-access":
                config.setEndpointType("REDIS");
                config.setDirection("outbound");
                config.setIsExternal(true);
                config.setKeyPattern("${keyPattern}");
                relationship.setType("ACCESSES");
                relationship.setDirection("function -> endpoint");
                config.setRelationship(relationship);
                break;
                
            case "db-access":
                config.setEndpointType("DB");
                config.setDirection("outbound");
                config.setIsExternal(true);
                config.setTableName("${tableName}");
                relationship.setType("ACCESSES");
                relationship.setDirection("function -> endpoint");
                config.setRelationship(relationship);
                break;
                
            default:
                log.warn("未知的规则类型: {}", type);
                return null;
        }
        
        return config;
    }
    
    /**
     * 获取有效的 BuildConfig（如果没有显式配置，则使用默认配置）
     */
    public static BuildConfig getEffectiveBuildConfig(String type, BuildConfig explicitConfig) {
        // 如果显式配置了 build，直接使用
        if (explicitConfig != null) {
            return explicitConfig;
        }
        
        // 否则根据 type 生成默认配置
        return createDefault(type);
    }
}

