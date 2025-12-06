package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.model.CodeRelationship;
import com.poseidon.codegraph.engine.domain.parser.filter.FilterPipeline;
import com.poseidon.codegraph.engine.domain.parser.filter.RelationshipFilter;
import org.eclipse.jdt.core.dom.IMethodBinding;

/**
 * 抽象源码解析器
 * 提供通用的解析流程控制和过滤器支持
 */
public abstract class AbstractSourceCodeParser implements SourceCodeParser {

    protected final RelationshipFilter filterPipeline;

    protected AbstractSourceCodeParser() {
        // 默认初始化为空管道
        this.filterPipeline = new FilterPipeline();
    }

    protected AbstractSourceCodeParser(RelationshipFilter filterPipeline) {
        this.filterPipeline = filterPipeline != null ? filterPipeline : new FilterPipeline();
    }

    /**
     * 判断是否保留该关系
     * 子类在解析关系时调用此方法进行过滤
     */
    protected boolean shouldKeepRelationship(CodeRelationship relationship, IMethodBinding targetBinding) {
        return filterPipeline.shouldKeep(relationship, targetBinding);
    }
}
