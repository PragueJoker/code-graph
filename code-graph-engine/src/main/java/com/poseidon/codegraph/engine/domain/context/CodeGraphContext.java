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
     * 项目名称
     */
    private String projectName;
    
    /**
     * 文件绝对路径（用于读取内容）
     */
    private String absoluteFilePath;

    public String getAbsoluteFilePath() {
        // 领域逻辑：如果绝对路径为空，尝试使用当前工作目录 + 项目路径推导
        if (this.absoluteFilePath == null || this.absoluteFilePath.trim().isEmpty()) {
            if (this.projectFilePath != null && !this.projectFilePath.trim().isEmpty()) {
                try {
                    return java.nio.file.Path.of(System.getProperty("user.home"), this.projectFilePath)
                        .toAbsolutePath().toString();
                } catch (Exception e) {
                    // 忽略异常，返回原始值
                }
            }
        }
        return this.absoluteFilePath;
    }
    
    /**
     * 项目文件路径（用于标识，相对于 Git 根）
     */
    private String projectFilePath;
    
    /**
     * classpath 条目
     */
    private String[] classpathEntries;
    
    /**
     * sourcepath 条目
     */
    private String[] sourcepathEntries;
    
    /**
     * 旧文件项目路径
     */
    private String oldProjectFilePath;
    
    /**
     * 新文件项目路径
     */
    private String newProjectFilePath;
    
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

