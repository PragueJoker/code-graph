package com.poseidon.codegraph.engine.domain.parser.enricher;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * 代码图谱增强器接口
 * 
 * 职责：
 * - 在基础解析（Package、Unit、Function、Relationship）完成后，对 CodeGraph 进行增强
 * - 支持插件式扩展，可以添加端点、注解、指标等额外信息
 * 
 * 设计原则：
 * - 单一职责：每个实现只负责一种类型的增强
 * - 开闭原则：对扩展开放，对修改关闭
 * - 依赖倒置：解析器依赖抽象接口，而非具体实现
 * 
 * 扩展示例：
 * - EndpointEnricher：解析 HTTP/Kafka/Redis 等端点
 * - SecurityEnricher：解析安全注解（@PreAuthorize、@Secured）
 * - MetricsEnricher：解析性能指标注解（@Timed、@Counted）
 * - CacheEnricher：解析缓存注解（@Cacheable、@CacheEvict）
 */
public interface GraphEnricher {
    
    /**
     * 增强代码图谱
     * 
     * @param graph 已经解析好的基础代码图谱（包含 Package、Unit、Function、Relationship）
     * @param cu JDT 编译单元（用于访问 AST）
     * @param context 代码图谱上下文（包含文件信息、项目信息、Reader/Writer 等）
     */
    void enrich(CodeGraph graph, CompilationUnit cu, CodeGraphContext context);
    
    /**
     * 增强器名称（用于日志和调试）
     */
    String getName();
    
    /**
     * 增强器优先级（数字越小优先级越高，默认 100）
     * 用于控制多个增强器的执行顺序
     */
    default int getPriority() {
        return 100;
    }
    
    /**
     * 是否启用（可用于配置开关）
     */
    default boolean isEnabled() {
        return true;
    }
}

