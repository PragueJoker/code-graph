package com.poseidon.codegraph.engine.domain.parser;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Processor 注册表
 * 
 * 职责：
 * - 管理所有 Processor 的注册
 * - 提供便捷的工厂方法创建常用配置
 * - 领域层纯 Java，不依赖任何外部框架
 */
@Slf4j
public class ProcessorRegistry {
    
    private final List<ASTNodeProcessor> processors = new ArrayList<>();
    
    /**
     * 注册 Processor
     */
    public ProcessorRegistry register(ASTNodeProcessor processor) {
        processors.add(processor);
        log.debug("注册 Processor: {}, 优先级: {}", processor.getName(), processor.getPriority());
        return this;  // 支持链式调用
    }
    
    /**
     * 获取所有 Processor
     */
    public List<ASTNodeProcessor> getAll() {
        return new ArrayList<>(processors);
    }
    
    /**
     * 创建只包含核心解析的注册表
     * 核心解析包括：Package、Unit、Function 节点提取 + 结构关系 + 调用关系
     */
    public static ProcessorRegistry createCoreOnly() {
        ProcessorRegistry registry = new ProcessorRegistry();
        // 节点提取阶段
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.PackageProcessor());
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.UnitProcessor());
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.FunctionProcessor());
        // 关系构建阶段
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.CallRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.StructureRelationshipProcessor());
        log.info("创建 CoreOnly 注册表（5 个 Processor）");
        return registry;
    }
    
    /**
     * 创建包含核心+端点解析的注册表
     * 包括：核心解析（Package、Unit、Function、关系）+ 端点解析（HTTP、Kafka 等）
     */
    public static ProcessorRegistry createWithEndpoint() {
        ProcessorRegistry registry = new ProcessorRegistry();
        // 节点提取阶段
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.PackageProcessor());
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.UnitProcessor());
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.FunctionProcessor());
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.EndpointProcessor());
        // 关系构建阶段
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.CallRelationshipProcessor());
        registry.register(new com.poseidon.codegraph.engine.domain.parser.processor.StructureRelationshipProcessor());
        log.info("创建 WithEndpoint 注册表（6 个 Processor）");
        return registry;
    }
    
    /**
     * 创建空注册表（用于自定义配置）
     */
    public static ProcessorRegistry createEmpty() {
        return new ProcessorRegistry();
    }
}

