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
     * 项目文件路径(某个项目的绝对路径)
     * 比如你的项目code-graph存储到了git上，那这个项目下的所有节点的路径都是从code-graph开始的
     * 一句话节点的文件位置就是从项目所在的文件夹开始的
     */
    private String projectFilePath;
    
    /**
     * 起始行号
     */
    private Integer startLine;
    
    /**
     * 结束行号
     */
    private Integer endLine;

    public String getProjectFilePath() {
        return projectFilePath;
    }

    public void setProjectFilePath(String projectFilePath) {
        this.projectFilePath = projectFilePath;
    }
}

