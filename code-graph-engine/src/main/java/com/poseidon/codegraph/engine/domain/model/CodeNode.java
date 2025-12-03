package com.poseidon.codegraph.engine.domain.model;

import lombok.Data;

/**
 * 代码节点基类
 * 所有代码元素的抽象基类
 */
@Data
public abstract class CodeNode {
    /**
     * 唯一标识
     */
    private String id;
    
    /**
     * 名称
     */
    private String name;
    
    /**
     * 全限定名
     */
    private String qualifiedName;
    
    /**
     * 语言
     */
    private String language;
    
    /**
     * 文件路径（对于 Package 是目录路径）
     */
    private String filePath;
    
    /**
     * 起始行号
     */
    private Integer startLine;
    
    /**
     * 结束行号
     */
    private Integer endLine;
}

