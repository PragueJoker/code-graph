package com.poseidon.codegraph.engine.adapter.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建文件节点请求 DTO
 */
@Data
public class CreateFileNodesRequest {
    /**
     * 项目名称（Git 仓库名），用于生成全局唯一 ID
     */
    private String projectName;
    
    /**
     * 文件绝对路径（用于读取文件内容）
     */
    private String absoluteFilePath;
    
    /**
     * 项目文件路径（相对于 Git 根目录，用于存储 ID）
     * 例如：code-graph-engine/src/main/java/com/Example.java
     */
    private String projectFilePath;
    
    /**
     * Classpath 条目列表（JAR 文件路径、类目录路径等）
     */
    private List<String> classpathEntries;
    
    /**
     * Sourcepath 条目列表（源代码目录路径）
     */
    private List<String> sourcepathEntries;
}

