package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model;

import lombok.Data;
import java.util.List;

/**
 * 节点定位配置
 */
@Data
public class LocateConfig {
    
    /**
     * AST 节点类型
     * MethodDeclaration | MethodInvocation | ClassDeclaration | FieldDeclaration
     */
    private String nodeType;
    
    /**
     * 筛选条件
     */
    private List<WhereCondition> where;
    
    @Data
    public static class WhereCondition {
        // 注解检查
        private AnnotationCondition hasAnnotation;
        
        // 方法名检查
        private MethodNameCondition methodName;
        
        // 接收者检查
        private ReceiverCondition receiver;
        
        // 父类检查
        private ParentClassCondition parentClass;
        
        // 链式调用检查
        private String isPartOfChain;
    }
    
    @Data
    public static class AnnotationCondition {
        private String nameEquals;
        private String nameMatches;
        private List<String> nameIn;
    }
    
    @Data
    public static class MethodNameCondition {
        private String equals;
        private String matches;
        private List<String> in;
    }
    
    @Data
    public static class ReceiverCondition {
        private String type;
        private String typeMatches;
        private String name;
        private String isTrackedObject;
    }
    
    @Data
    public static class ParentClassCondition {
        private AnnotationCondition hasAnnotation;
    }
}




