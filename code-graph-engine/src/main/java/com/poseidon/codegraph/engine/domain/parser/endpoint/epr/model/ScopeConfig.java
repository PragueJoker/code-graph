package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model;

import lombok.Data;
import java.util.List;

/**
 * 作用域配置
 * 定义规则应用的范围
 */
@Data
public class ScopeConfig {
    
    /**
     * 包路径包含模式
     * 支持通配符：** 表示任意层级，* 表示单层
     */
    private List<String> packageIncludes;
    
    /**
     * 包路径排除模式
     */
    private List<String> packageExcludes;
    
    /**
     * 文件名模式
     */
    private List<String> fileNamePatterns;
    
    /**
     * 类注解要求
     */
    private List<String> classAnnotations;
}

