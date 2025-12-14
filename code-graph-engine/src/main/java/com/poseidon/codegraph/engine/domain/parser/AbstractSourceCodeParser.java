package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import com.poseidon.codegraph.engine.domain.model.CodeRelationship;
import com.poseidon.codegraph.engine.domain.parser.enricher.GraphEnricher;
import com.poseidon.codegraph.engine.domain.parser.filter.FilterPipeline;
import com.poseidon.codegraph.engine.domain.parser.filter.RelationshipFilter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 抽象源码解析器
 * 提供通用的解析流程控制、过滤器支持和增强器支持
 */
@Slf4j
public abstract class AbstractSourceCodeParser implements SourceCodeParser {

    protected final RelationshipFilter filterPipeline;
    protected final List<GraphEnricher> enrichers;

    protected AbstractSourceCodeParser() {
        // 默认初始化为空管道和空增强器列表
        this.filterPipeline = new FilterPipeline();
        this.enrichers = new ArrayList<>();
    }

    protected AbstractSourceCodeParser(RelationshipFilter filterPipeline) {
        this.filterPipeline = filterPipeline != null ? filterPipeline : new FilterPipeline();
        this.enrichers = new ArrayList<>();
    }
    
    protected AbstractSourceCodeParser(RelationshipFilter filterPipeline, List<GraphEnricher> enrichers) {
        this.filterPipeline = filterPipeline != null ? filterPipeline : new FilterPipeline();
        this.enrichers = enrichers != null ? enrichers : new ArrayList<>();
    }

    /**
     * 判断是否保留该关系
     * 子类在解析关系时调用此方法进行过滤
     */
    protected boolean shouldKeepRelationship(CodeRelationship relationship, IMethodBinding targetBinding) {
        return filterPipeline.shouldKeep(relationship, targetBinding);
    }
    
    /**
     * 应用增强器到解析结果
     * 子类在完成基础解析后调用此方法
     * 
     * @param graph 已解析的代码图谱
     * @param cu 编译单元（AST）
     * @param context 代码图谱上下文
     */
    protected void applyEnrichers(CodeGraph graph, CompilationUnit cu, CodeGraphContext context) {
        if (enrichers.isEmpty()) {
            log.debug("没有配置增强器，跳过增强步骤");
            return;
        }
        
        // 按优先级排序
        List<GraphEnricher> sortedEnrichers = new ArrayList<>(enrichers);
        sortedEnrichers.sort(Comparator.comparingInt(GraphEnricher::getPriority));
        
        // 依次应用增强器
        for (GraphEnricher enricher : sortedEnrichers) {
            if (!enricher.isEnabled()) {
                log.debug("增强器 {} 未启用，跳过", enricher.getName());
                continue;
            }
            
            try {
                log.debug("应用增强器: {}", enricher.getName());
                enricher.enrich(graph, cu, context);
            } catch (Exception e) {
                log.error("增强器 {} 执行失败: {}", enricher.getName(), e.getMessage(), e);
                // 不抛出异常，允许其他增强器继续执行
            }
        }
    }
}
