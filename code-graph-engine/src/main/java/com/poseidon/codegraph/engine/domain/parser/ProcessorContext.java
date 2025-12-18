package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import lombok.Data;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

/**
 * Processor 上下文
 * 
 * 职责：
 * - 在主 AST 遍历流程中传递，所有 Processor 共享
 * - 提供对 CodeGraph 的访问（所有 Processor 写入同一个 graph）
 * - 提供对 AST 的访问（CompilationUnit）
 * - 提供当前遍历位置的上下文信息（当前类、当前方法）
 * - 提供项目元信息（文件路径、Git 信息等）
 */
@Data
public class ProcessorContext {
    
    // ===== 核心数据 =====
    
    /**
     * 代码图谱（所有 Processor 的输出汇总到这里）
     */
    private final CodeGraph graph = new CodeGraph();
    
    /**
     * JDT 编译单元（AST 根节点）
     */
    private CompilationUnit compilationUnit;
    
    // ===== 项目信息 =====
    
    /**
     * 绝对文件路径
     */
    private String absoluteFilePath;
    
    /**
     * 项目名称
     */
    private String projectName;
    
    /**
     * 项目相对文件路径
     */
    private String projectFilePath;
    
    /**
     * Git 仓库 URL
     */
    private String gitRepoUrl;
    
    /**
     * Git 分支
     */
    private String gitBranch;
    
    /**
     * 包名
     */
    private String packageName;
    
    // ===== 遍历上下文（当前位置）=====
    
    /**
     * 当前正在处理的类型声明（方便 Processor 获取所属类信息）
     */
    private TypeDeclaration currentType;
    
    /**
     * 当前正在处理的方法声明（方便 Processor 获取所属方法信息）
     */
    private MethodDeclaration currentMethod;
    
    /**
     * 获取 CodeGraph
     */
    public CodeGraph getGraph() {
        return graph;
    }
}
