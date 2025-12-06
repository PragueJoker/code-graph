package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.*;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import com.poseidon.codegraph.engine.domain.model.event.CodeChangeEvent;
import com.poseidon.codegraph.engine.domain.parser.SourceCodeParser;
import com.poseidon.codegraph.engine.domain.parser.JdtSourceCodeParser;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
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
            context.getClasspathEntries(),
            context.getSourcepathEntries()
        );
    }
    
    protected CodeGraph parseFile(CodeGraphContext context, String absoluteFilePath, String projectFilePath) {
        return createParser(context).parse(absoluteFilePath, context.getProjectName(), projectFilePath);
    }
    
    protected void saveNodes(CodeGraph graph, CodeGraphContext context) {
        // 批量保存：先查询是否存在，存在则更新，不存在则插入
        
        // 1. 处理 Packages
        java.util.List<CodePackage> packages = graph.getPackagesAsList();
        if (!packages.isEmpty()) {
            savePackagesWithCheck(packages, context);
        }
        
        // 2. 处理 Units
        java.util.List<CodeUnit> units = graph.getUnitsAsList();
        if (!units.isEmpty()) {
            saveUnitsWithCheck(units, context);
        }
        
        // 3. 处理 Functions
        java.util.List<CodeFunction> functions = graph.getFunctionsAsList();
        if (!functions.isEmpty()) {
            saveFunctionsWithCheck(functions, context);
        }
        
        // 4. 处理结构关系（BELONGS_TO）
        java.util.List<CodeRelationship> allRelationships = graph.getRelationshipsAsList();
        java.util.List<CodeRelationship> structureRelationships = allRelationships.stream()
            .filter(rel -> rel.getRelationshipType() == RelationshipType.PACKAGE_TO_UNIT 
                       || rel.getRelationshipType() == RelationshipType.UNIT_TO_FUNCTION)
            .collect(java.util.stream.Collectors.toList());
        
        if (!structureRelationships.isEmpty()) {
            saveStructureRelationshipsWithCheck(structureRelationships, context);
        }
    }
    
    /**
     * 批量保存包：先查询存在性，然后分离插入/更新
     */
    private void savePackagesWithCheck(java.util.List<CodePackage> packages, CodeGraphContext context) {
        // 查询哪些包已存在
        java.util.List<String> packageIds = packages.stream()
            .map(CodePackage::getId)
            .collect(java.util.stream.Collectors.toList());
        java.util.Set<String> existingIds = context.getReader()
            .getFindExistingPackagesByQualifiedNames()
            .apply(packageIds);
        
        // 分离需要插入和更新的包
        java.util.List<CodePackage> toInsert = new java.util.ArrayList<>();
        java.util.List<CodePackage> toUpdate = new java.util.ArrayList<>();
        
        for (CodePackage pkg : packages) {
            if (existingIds.contains(pkg.getId())) {
                toUpdate.add(pkg);
            } else {
                toInsert.add(pkg);
            }
        }
        
        // 批量插入和更新
        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertPackagesBatch().accept(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            context.getWriter().getUpdatePackagesBatch().accept(toUpdate);
        }
    }
    
    /**
     * 批量保存单元：先查询存在性，然后分离插入/更新
     */
    private void saveUnitsWithCheck(java.util.List<CodeUnit> units, CodeGraphContext context) {
        // 查询哪些单元已存在
        java.util.List<String> unitIds = units.stream()
            .map(CodeUnit::getId)
            .collect(java.util.stream.Collectors.toList());
        java.util.Set<String> existingIds = context.getReader()
            .getFindExistingUnitsByQualifiedNames()
            .apply(unitIds);
        
        // 分离需要插入和更新的单元
        java.util.List<CodeUnit> toInsert = new java.util.ArrayList<>();
        java.util.List<CodeUnit> toUpdate = new java.util.ArrayList<>();
        
        for (CodeUnit unit : units) {
            if (existingIds.contains(unit.getId())) {
                toUpdate.add(unit);
            } else {
                toInsert.add(unit);
            }
        }
        
        // 批量插入和更新
        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertUnitsBatch().accept(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            context.getWriter().getUpdateUnitsBatch().accept(toUpdate);
        }
    }
    
    /**
     * 批量保存函数：先查询存在性，然后分离插入/更新
     */
    private void saveFunctionsWithCheck(java.util.List<CodeFunction> functions, CodeGraphContext context) {
        // 查询哪些函数已存在
        java.util.List<String> functionIds = functions.stream()
            .map(CodeFunction::getId)
            .collect(java.util.stream.Collectors.toList());
        java.util.Set<String> existingIds = context.getReader()
            .getFindExistingFunctionsByQualifiedNames()
            .apply(functionIds);
        
        // 分离需要插入和更新的函数
        java.util.List<CodeFunction> toInsert = new java.util.ArrayList<>();
        java.util.List<CodeFunction> toUpdate = new java.util.ArrayList<>();
        
        for (CodeFunction func : functions) {
            if (existingIds.contains(func.getId())) {
                toUpdate.add(func);
            } else {
                toInsert.add(func);
            }
        }
        
        // 批量插入和更新
        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertFunctionsBatch().accept(toInsert);
        }
        if (!toUpdate.isEmpty()) {
            context.getWriter().getUpdateFunctionsBatch().accept(toUpdate);
        }
    }
    
    /**
     * 批量保存结构关系：直接插入（结构关系是稳定的，通常不会重复）
     */
    private void saveStructureRelationshipsWithCheck(java.util.List<CodeRelationship> relationships, CodeGraphContext context) {
        // 结构关系通常是新创建的，直接批量插入即可
        context.getWriter().getInsertRelationshipsBatch().accept(relationships);
        log.info("批量插入结构关系: count={}", relationships.size());
    }
    
    protected void deleteNodes(List<CodeUnit> units, List<CodeFunction> fileFunctions, 
                            CodeGraphContext context) {
        units.forEach(unit -> context.getWriter().getDeleteNode().accept(unit.getId()));
        fileFunctions.forEach(func -> context.getWriter().getDeleteNode().accept(func.getId()));
    }
    
    protected int rebuildFileCallRelationships(CodeGraphContext context, String absoluteFilePath, String projectFilePath,
                                            CodeGraph graph) {
        log.debug("开始重建调用关系: file={}", projectFilePath);
        
        if (graph == null) {
            log.debug("graph 为空，重新解析文件: {}", projectFilePath);
            graph = parseFile(context, absoluteFilePath, projectFilePath);
        }
        
        java.util.List<CodeRelationship> relationships = graph.getRelationshipsAsList();
        // 只处理 CALLS 关系
        java.util.List<CodeRelationship> callRelationships = relationships.stream()
            .filter(rel -> rel.getRelationshipType() == RelationshipType.CALLS)
            .collect(java.util.stream.Collectors.toList());
        
        if (callRelationships.isEmpty()) {
            log.info("文件没有调用关系: file={}", projectFilePath);
            return 0;
        }
        
        log.info("文件包含 {} 条调用关系: file={}", callRelationships.size(), projectFilePath);
        
        // 批量查询：收集所有需要检查的节点ID
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        for (CodeRelationship rel : callRelationships) {
            nodeIds.add(rel.getFromFunctionId());
            nodeIds.add(rel.getToFunctionId());
        }
        
        log.debug("收集到 {} 个需要检查的节点ID", nodeIds.size());
        
        // 批量查询哪些节点已存在
        java.util.Set<String> existingIds = context.getReader()
            .getFindExistingFunctionsByQualifiedNames()
            .apply(new java.util.ArrayList<>(nodeIds));
        
        log.debug("数据库中已存在 {} 个节点", existingIds.size());
        
        // 批量创建占位符节点（不存在的节点）
        java.util.List<CodeFunction> placeholders = new java.util.ArrayList<>();
        for (String nodeId : nodeIds) {
            if (!existingIds.contains(nodeId)) {
                // 从 relationships 中获取 language（假设同一个文件的关系使用相同语言）
                String language = relationships.get(0).getLanguage();
                CodeFunction placeholder = createPlaceholderFunction(nodeId, language);
                placeholders.add(placeholder);
                log.debug("需要创建占位符节点: {}", nodeId);
            }
        }
        
        // 批量插入占位符节点（占位符节点是新创建的，直接插入）
        if (!placeholders.isEmpty()) {
            log.info("批量创建占位符节点: count={}", placeholders.size());
            context.getWriter().getInsertFunctionsBatch().accept(placeholders);
        }
        
        // 批量插入调用关系（调用关系是新创建的，直接插入）
        log.info("批量插入调用关系: count={}", callRelationships.size());
        context.getWriter().getInsertRelationshipsBatch().accept(callRelationships);
        
        return callRelationships.size();
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
        
        log.info("开始处理级联变更: dependentCount={}", dependentFiles.size());
        
        for (String depProjectFile : dependentFiles) {
            if (context.getSender() != null && context.getSender().getSendEvent() != null) {
                CodeChangeEvent event = new CodeChangeEvent();
                event.setEventId(UUID.randomUUID().toString());
                event.setProjectName(context.getProjectName());
                
                // 级联变更无法获取绝对路径，设为 null
                // 依赖 IncrementalUpdateService 的兜底逻辑进行推导
                event.setAbsoluteFilePath(null);
                
                event.setClasspathEntries(context.getClasspathEntries());
                event.setSourcepathEntries(context.getSourcepathEntries());
                event.setOldFileIdentifier(depProjectFile);
                event.setChangeType(ChangeType.CASCADE_UPDATE);
                event.setLanguage("java");
                event.setTimestamp(LocalDateTime.now());
                event.setReason("Cascade update from " + context.getOldProjectFilePath());
                
                context.getSender().getSendEvent().accept(event);
                log.debug("已发送级联变更事件: file={}", depProjectFile);
            } else {
                log.warn("未配置事件发送器，忽略级联变更: {}", depProjectFile);
            }
        }
    }
    
}