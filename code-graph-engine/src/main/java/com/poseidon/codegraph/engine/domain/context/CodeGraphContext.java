package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import lombok.Data;

/**
 * 代码图谱上下文（领域层交互核心对象）
 * 包含：
 * 1. 变更数据（参数）
 * 2. 操作能力（Reader/Writer）
 * 3. 事件能力（Sender）
 */
@Data
public class CodeGraphContext {
    
    // ========== 变更参数 ==========
    
    /**
     * 项目根目录
     */
    private String projectRoot;
    
    /**
     * classpath 条目
     */
    private String[] classpathEntries;
    
    /**
     * sourcepath 条目
     */
    private String[] sourcepathEntries;
    
    /**
     * 旧文件路径
     */
    private String oldFilePath;
    
    /**
     * 新文件路径
     */
    private String newFilePath;
    
    /**
     * 变更类型
     */
    private ChangeType changeType;
    
    // ========== 操作能力 ==========
    
    /**
     * 图谱读取器
     */
    private GraphReader reader;
    
    /**
     * 图谱写入器
     */
    private GraphWriter writer;
    
    /**
     * 图谱事件发送器
     */
    private GraphSender sender;
    
    public CodeGraphContext() {
        this.reader = new GraphReader();
        this.writer = new GraphWriter();
        this.sender = new GraphSender();
    }
}

