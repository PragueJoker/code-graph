package com.poseidon.codegraph.engine.adapter.dto;

import lombok.Data;

import java.util.List;

/**
 * 创建文件节点请求 DTO
 */
@Data
public class CreateFileNodesRequest {
    /**
     * 项目根目录路径
     */
    private String projectRoot;
    
    /**
     * 文件路径（相对于项目根目录或绝对路径）
     */
    private String filePath;
    
    /**
     * Classpath 条目列表（JAR 文件路径、类目录路径等）
     */
    private List<String> classpathEntries;
    
    /**
     * Sourcepath 条目列表（源代码目录路径）
     */
    private List<String> sourcepathEntries;
}

