package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 信息提取配置
 */
@Data
public class ExtractConfig {
    
    // 简单提取
    private String from;
    private String trace;
    
    // 映射
    private Map<String, Object> mapping;
    
    // 组合
    private CombineConfig combine;
    
    // 多策略
    private List<StrategyConfig> strategies;
    
    // 通用选项
    private Object defaultValue;
    private Boolean required;
    private Boolean optional;
    private Object transform;
    
    @Data
    public static class CombineConfig {
        private List<CombineSource> sources;
        private String separator;
        private Boolean normalize;
    }
    
    @Data
    public static class CombineSource {
        private String source;
        private String literal;
    }
    
    @Data
    public static class StrategyConfig {
        private TryConfig tryConfig;
        private String onSuccess;
        
        // 为了支持YAML的 try: {...}
        public void setTry(TryConfig tryConfig) {
            this.tryConfig = tryConfig;
        }
        
        public TryConfig getTry() {
            return tryConfig;
        }
    }
    
    @Data
    public static class TryConfig {
        private String from;
        private String pattern;
        private Integer captureGroup;
        private String transform;
        private Object defaultValue;
        private Map<String, Object> mapping;
    }
}

