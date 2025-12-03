package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.*;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import com.poseidon.codegraph.engine.domain.model.event.CodeChangeEvent;
import com.poseidon.codegraph.engine.domain.parser.SourceCodeParser;
import com.poseidon.codegraph.engine.domain.parser.JdtSourceCodeParser;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 抽象代码变更处理器
 * 提供通用辅助方法
 */
@Slf4j
public abstract class AbstractChangeProcessor implements CodeChangeProcessor {
    
    protected AbstractChangeProcessor() {
    }
    
    /**
     * 创建绑定的解析器
     */
    protected SourceCodeParser createParser(CodeGraphContext context) {
        return new JdtSourceCodeParser(
            context.getProjectRoot(),
            context.getClasspathEntries(),
            context.getSourcepathEntries()
        );
    }
    
    protected CodeGraph parseFile(CodeGraphContext context, String filePath) {
        return createParser(context).parse(filePath);
    }
    
    protected void saveNodes(CodeGraph graph, CodeGraphContext context) {
        // 批量保存：先查询是否存在，存在则更新，不存在则插入
        java.util.List<CodePackage> packages = graph.getPackagesAsList();
        if (!packages.isEmpty()) {
            context.getWriter().getSavePackagesBatch().accept(packages);
        }
        
        java.util.List<CodeUnit> units = graph.getUnitsAsList();
        if (!units.isEmpty()) {
            context.getWriter().getSaveUnitsBatch().accept(units);
        }
        
        java.util.List<CodeFunction> functions = graph.getFunctionsAsList();
        if (!functions.isEmpty()) {
            context.getWriter().getSaveFunctionsBatch().accept(functions);
        }
    }
    
    protected void deleteNodes(List<CodeUnit> units, List<CodeFunction> fileFunctions, 
                            CodeGraphContext context) {
        units.forEach(unit -> context.getWriter().getDeleteNode().accept(unit.getId()));
        fileFunctions.forEach(func -> context.getWriter().getDeleteNode().accept(func.getId()));
    }
    
    protected int rebuildFileCallRelationships(CodeGraphContext context, String filePath, 
                                            CodeGraph graph) {
        if (graph == null) {
            graph = parseFile(context, filePath);
        }
        
        java.util.List<CallRelationship> relationships = graph.getRelationshipsAsList();
        if (relationships.isEmpty()) {
            return 0;
        }
        
        // 批量查询：收集所有需要检查的节点ID
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        for (CallRelationship rel : relationships) {
            nodeIds.add(rel.getFromFunctionId());
            nodeIds.add(rel.getToFunctionId());
        }
        
        // 批量查询哪些节点已存在
        java.util.Set<String> existingIds = context.getReader()
            .getFindExistingFunctionsByQualifiedNames()
            .apply(new java.util.ArrayList<>(nodeIds));
        
        // 批量创建占位符节点（不存在的节点）
        java.util.List<CodeFunction> placeholders = new java.util.ArrayList<>();
        for (String nodeId : nodeIds) {
            if (!existingIds.contains(nodeId)) {
                // 从 relationships 中获取 language（假设同一个文件的关系使用相同语言）
                String language = relationships.get(0).getLanguage();
                CodeFunction placeholder = createPlaceholderFunction(nodeId, language);
                placeholders.add(placeholder);
                log.debug("创建占位符节点: {}", nodeId);
            }
        }
        
        // 批量保存占位符节点
        if (!placeholders.isEmpty()) {
            context.getWriter().getSaveFunctionsBatch().accept(placeholders);
            log.debug("批量创建占位符节点: {} 个", placeholders.size());
        }
        
        // 批量保存调用关系
        context.getWriter().getSaveCallRelationshipsBatch().accept(relationships);
        log.debug("批量保存调用关系: {} 条", relationships.size());
        
        return relationships.size();
    }
    
    /**
     * 创建占位符函数节点
     * 业务规则：当调用关系的目标节点不存在时，创建占位符节点
     * 
     * @param qualifiedName 全限定名
     * @param language 语言
     * @return 占位符节点
     */
    private CodeFunction createPlaceholderFunction(String qualifiedName, String language) {
        CodeFunction placeholder = new CodeFunction();
        placeholder.setId(qualifiedName);
        placeholder.setQualifiedName(qualifiedName);
        placeholder.setIsPlaceholder(true);
        placeholder.setLanguage(language);
        return placeholder;
    }
    
    protected void triggerCascadeChanges(CodeGraphContext context, List<String> dependentFiles) {
        if (dependentFiles.isEmpty()) {
            return;
        }
        
        log.info("开始处理级联变更...");
        
        for (String depFile : dependentFiles) {
            if (context.getSender() != null && context.getSender().getSendEvent() != null) {
                CodeChangeEvent event = new CodeChangeEvent();
                event.setEventId(UUID.randomUUID().toString());
                event.setProjectRoot(context.getProjectRoot());
                event.setClasspathEntries(context.getClasspathEntries());
                event.setSourcepathEntries(context.getSourcepathEntries());
                event.setOldFileIdentifier(depFile);
                event.setChangeType(ChangeType.CASCADE_UPDATE);
                event.setLanguage("java");
                event.setTimestamp(LocalDateTime.now());
                event.setReason("Cascade update from " + context.getOldFilePath());
                
                context.getSender().getSendEvent().accept(event);
                log.debug("已发送级联变更事件: {}", depFile);
            } else {
                log.warn("未配置事件发送器，忽略级联变更: {}", depFile);
            }
        }
    }
}
