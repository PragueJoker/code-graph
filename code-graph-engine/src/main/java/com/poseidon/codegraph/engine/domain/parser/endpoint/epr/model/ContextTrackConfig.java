package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 上下文追踪配置
 * 用于追踪 Router 等对象
 */
@Data
public class ContextTrackConfig {
    
    /**
     * 追踪对象配置
     */
    private TrackObjectConfig trackObject;
    
    /**
     * 追踪链式调用配置
     */
    private TrackChainConfig trackChain;
    
    @Data
    public static class TrackObjectConfig {
        /**
         * 对象名称（用于引用）
         */
        private String name;
        
        /**
         * 创建时机
         */
        private OnCreateConfig onCreate;
        
        /**
         * 识别方式：assignedVariable | methodReceiver
         */
        private String identifyBy;
        
        /**
         * 提取的属性
         */
        private Map<String, PropertyExtractConfig> extractProperties;
    }
    
    @Data
    public static class TrackChainConfig {
        /**
         * 链名称
         */
        private String name;
        
        /**
         * 链起点
         */
        private OnCreateConfig start;
        
        /**
         * 从起点提取的属性
         */
        private Map<String, PropertyExtractConfig> extractFromStart;
    }
    
    @Data
    public static class OnCreateConfig {
        /**
         * 方法调用匹配
         */
        private MethodInvocationPattern methodInvocation;
        
        /**
         * 构造函数匹配
         */
        private ConstructorPattern constructor;
    }
    
    @Data
    public static class MethodInvocationPattern {
        private String className;
        private List<String> methodName;
    }
    
    @Data
    public static class ConstructorPattern {
        private String className;
    }
    
    @Data
    public static class PropertyExtractConfig {
        /**
         * 数据来源
         */
        private String from;
        
        /**
         * 追踪模式：auto | none
         */
        private String trace;
        
        /**
         * 值类型：string | variable
         */
        private String type;
        
        /**
         * 默认值
         */
        private Object defaultValue;
        
        /**
         * 是否可选
         */
        private Boolean optional;
    }
}

