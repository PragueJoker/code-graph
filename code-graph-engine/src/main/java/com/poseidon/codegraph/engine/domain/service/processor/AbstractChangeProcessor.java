package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.*;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import com.poseidon.codegraph.engine.domain.model.event.CodeChangeEvent;
import com.poseidon.codegraph.engine.domain.parser.SourceCodeParser;
import com.poseidon.codegraph.engine.domain.parser.JdtSourceCodeParser;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
        // 如果配置了增强器，传递给解析器
        if (context.getEnrichers() != null && !context.getEnrichers().isEmpty()) {
            return new JdtSourceCodeParser(
                context.getClasspathEntries(),
                context.getSourcepathEntries(),
                context.getEnrichers()
            );
        } else {
            return new JdtSourceCodeParser(
                context.getClasspathEntries(),
                context.getSourcepathEntries()
            );
        }
    }
    
    protected CodeGraph parseFile(CodeGraphContext context, String absoluteFilePath, String projectFilePath) {
        return createParser(context).parse(
            absoluteFilePath, 
            context.getProjectName(), 
            projectFilePath,
            context.getGitRepoUrl(),
            context.getGitBranch()
        );
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
        
        // 4. 处理 Endpoints（端点）
        java.util.List<CodeEndpoint> endpoints = graph.getEndpointsAsList();
        if (!endpoints.isEmpty()) {
            saveEndpointsWithCheck(endpoints, context);
            // 保存端点后，尝试创建 MATCHES 关系
            createEndpointMatchRelationships(endpoints, context);
        }
        
        // 5. 处理结构关系（BELONGS_TO 和端点关系）
        java.util.List<CodeRelationship> allRelationships = graph.getRelationshipsAsList();
        java.util.List<CodeRelationship> structureRelationships = allRelationships.stream()
            .filter(rel -> rel.getRelationshipType() == RelationshipType.PACKAGE_TO_UNIT 
                       || rel.getRelationshipType() == RelationshipType.UNIT_TO_FUNCTION
                       || rel.getRelationshipType() == RelationshipType.ENDPOINT_TO_FUNCTION
                       || rel.getRelationshipType() == RelationshipType.FUNCTION_TO_ENDPOINT)
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
     * 创建端点匹配关系（MATCHES）
     * 
     * 策略：
     * - 只在两端都存在时才创建关系
     * - 不创建 placeholder 端点
     * - 双向关系：outbound ↔ inbound
     */
    private void createEndpointMatchRelationships(java.util.List<CodeEndpoint> endpoints, CodeGraphContext context) {
        if (endpoints.isEmpty()) {
            return;
        }
        
        log.info("开始创建端点匹配关系，共 {} 个端点", endpoints.size());
        
        java.util.List<CodeRelationship> matchRelationships = new java.util.ArrayList<>();
        
        for (CodeEndpoint endpoint : endpoints) {
            // 只处理有 normalizedPath 的端点
            if (endpoint.getNormalizedPath() == null || endpoint.getNormalizedPath().isEmpty()) {
                log.debug("端点没有 normalizedPath，跳过: {}", endpoint.getId());
                continue;
            }
            
            // 根据方向查找匹配的对端
            String targetDirection = "inbound".equals(endpoint.getDirection()) ? "outbound" : "inbound";
            
            // 查询数据库中所有匹配的对端端点
            java.util.List<CodeEndpoint> matchingEndpoints = context.getReader()
                .getFindEndpointsByNormalizedPath()
                .apply(endpoint.getNormalizedPath(), targetDirection);
            
            log.debug("端点 {} ({}) 找到 {} 个匹配的 {} 端点",
                endpoint.getPath(), endpoint.getDirection(), 
                matchingEndpoints.size(), targetDirection);
            
            // 为每个匹配的对端创建 MATCHES 关系
            for (CodeEndpoint matchingEndpoint : matchingEndpoints) {
                CodeRelationship rel = new CodeRelationship();
                rel.setId(java.util.UUID.randomUUID().toString());
                rel.setRelationshipType(RelationshipType.MATCHES);
                
                // 关系方向：outbound -> inbound (为了查询方便)
                if ("outbound".equals(endpoint.getDirection())) {
                    rel.setFromNodeId(endpoint.getId());
                    rel.setToNodeId(matchingEndpoint.getId());
                } else {
                    rel.setFromNodeId(matchingEndpoint.getId());
                    rel.setToNodeId(endpoint.getId());
                }
                
                rel.setLanguage("java");
                matchRelationships.add(rel);
                
                log.debug("创建 MATCHES 关系: {} ({}) -> {} ({})",
                    endpoint.getPath(), endpoint.getDirection(),
                    matchingEndpoint.getPath(), matchingEndpoint.getDirection());
            }
        }
        
        // 批量保存 MATCHES 关系
        if (!matchRelationships.isEmpty()) {
            context.getWriter().getInsertRelationshipsBatch().accept(matchRelationships);
            log.info("✓ 端点匹配关系创建完成: 共创建 {} 个 MATCHES 关系", matchRelationships.size());
        } else {
            log.info("✓ 没有匹配的端点，无需创建 MATCHES 关系");
        }
    }
    
    /**
     * 批量保存端点：先去重，再查询存在性，最后只插入新端点
     * 注意：端点属性稳定，不需要更新已存在的端点
     */
    private void saveEndpointsWithCheck(java.util.List<CodeEndpoint> endpoints, CodeGraphContext context) {
        // 1. 先按 ID 去重（同一次解析中，相同 ID 的端点只保留第一个）
        java.util.Map<String, CodeEndpoint> uniqueEndpoints = new java.util.LinkedHashMap<>();
        for (CodeEndpoint endpoint : endpoints) {
            uniqueEndpoints.putIfAbsent(endpoint.getId(), endpoint);
        }
        
        java.util.List<CodeEndpoint> deduplicatedEndpoints = new java.util.ArrayList<>(uniqueEndpoints.values());
        
        log.info("端点去重：原始 {} 个，去重后 {} 个", 
            endpoints.size(), deduplicatedEndpoints.size());
        
        // 2. 查询哪些端点已在数据库中存在
        java.util.List<String> endpointIds = deduplicatedEndpoints.stream()
            .map(CodeEndpoint::getId)
            .collect(java.util.stream.Collectors.toList());
        
        java.util.Set<String> existingIds = context.getReader()
            .getFindExistingEndpointsByIds()
            .apply(endpointIds);
        
        // 3. 只插入不存在的端点（跳过已存在的）
        java.util.List<CodeEndpoint> toInsert = deduplicatedEndpoints.stream()
            .filter(e -> !existingIds.contains(e.getId()))
            .collect(java.util.stream.Collectors.toList());
        
        // 批量插入新端点
        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertEndpointsBatch().accept(toInsert);
        }
        
        log.info("端点保存完成：去重后 {} 个，新插入 {} 个，已存在 {} 个（跳过）",
            deduplicatedEndpoints.size(), toInsert.size(), existingIds.size());
    }
    
    /**
     * 批量保存结构关系：先查询存在性，再分离插入
     */
    private void saveStructureRelationshipsWithCheck(java.util.List<CodeRelationship> relationships, CodeGraphContext context) {
        if (relationships.isEmpty()) {
            return;
        }
        
        // 查询哪些结构关系已存在
        java.util.List<com.poseidon.codegraph.engine.application.model.CodeRelationshipDO> relationshipDOs = relationships.stream()
            .map(com.poseidon.codegraph.engine.application.converter.CodeGraphConverter::toDO)
            .collect(java.util.stream.Collectors.toList());
        
        java.util.Set<String> existingKeys = context.getReader().getFindExistingStructureRelationships() != null
            ? context.getReader().getFindExistingStructureRelationships().apply(relationshipDOs)
            : new java.util.HashSet<>();
        
        // 分离需要插入的关系
        java.util.List<CodeRelationship> toInsert = new java.util.ArrayList<>();
        
        for (CodeRelationship rel : relationships) {
            String key = rel.getFromNodeId() + ":" + rel.getToNodeId() + ":" + rel.getRelationshipType();
            if (!existingKeys.contains(key)) {
                toInsert.add(rel);
            }
        }
        
        // 批量插入新关系（结构关系不需要更新，因为没有可变属性）
        if (!toInsert.isEmpty()) {
            context.getWriter().getInsertRelationshipsBatch().accept(toInsert);
            log.info("批量插入结构关系: count={}", toInsert.size());
        } else {
            log.debug("所有结构关系已存在，跳过插入");
        }
    }
    
    protected void deleteNodes(List<CodeUnit> units, List<CodeFunction> fileFunctions, 
                            List<com.poseidon.codegraph.engine.domain.model.CodeEndpoint> endpoints,
                            CodeGraphContext context) {
        units.forEach(unit -> context.getWriter().getDeleteNode().accept(unit.getId()));
        fileFunctions.forEach(func -> context.getWriter().getDeleteNode().accept(func.getId()));
        endpoints.forEach(endpoint -> context.getWriter().getDeleteNode().accept(endpoint.getId()));
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
            nodeIds.add(rel.getFromNodeId());
            nodeIds.add(rel.getToNodeId());
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
     * @param qualifiedName 全限定名（格式：com.example.Class.method(params)）
     * @param language 语言
     * @return 占位符节点
     */
    private CodeFunction createPlaceholderFunction(String qualifiedName, String language) {
        CodeFunction placeholder = new CodeFunction();
        placeholder.setId(qualifiedName);
        placeholder.setQualifiedName(qualifiedName);
        placeholder.setIsPlaceholder(true);
        placeholder.setLanguage(language);
        
        // 从 qualifiedName 中提取 name 和 signature
        // 格式：com.example.Class.method(params):returnType 或 com.example.Class.method(params)
        String nameAndSignature = extractMethodNameAndSignature(qualifiedName);
        if (nameAndSignature != null) {
            // 如果包含参数，提取方法名
            int leftParen = nameAndSignature.indexOf('(');
            if (leftParen > 0) {
                String methodName = nameAndSignature.substring(0, leftParen);
                placeholder.setName(methodName);
                placeholder.setSignature(nameAndSignature);
            } else {
                placeholder.setName(nameAndSignature);
            }
        }
        
        return placeholder;
    }
    
    /**
     * 从 qualifiedName 中提取方法名和签名
     * 例如：com.example.Class.method(params) -> method(params)
     */
    private String extractMethodNameAndSignature(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return null;
        }
        
        // 先找到第一个 '('，确定方法签名的开始位置
        int leftParen = qualifiedName.indexOf('(');
        if (leftParen <= 0) {
            // 没有参数列表，直接找最后一个 '.'
            int lastDot = qualifiedName.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < qualifiedName.length() - 1) {
                return qualifiedName.substring(lastDot + 1);
            }
            return qualifiedName;
        }
        
        // 在 '(' 之前的部分找最后一个 '.'
        String beforeParams = qualifiedName.substring(0, leftParen);
        int lastDot = beforeParams.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < beforeParams.length() - 1) {
            // 返回 method(params) 部分
            return qualifiedName.substring(lastDot + 1);
        }
        
        return qualifiedName;
    }
    
    protected void triggerCascadeChanges(CodeGraphContext context, List<String> dependentFiles) {
        if (dependentFiles.isEmpty()) {
            return;
        }
        
        log.info("开始处理级联变更: dependentCount={}", dependentFiles.size());
        
        // 查询依赖文件的 Git 元信息
        List<FileMetadata> dependentFilesWithMeta = context.getReader().getFindWhoCallsMeWithMeta() != null
            ? context.getReader().getFindWhoCallsMeWithMeta().apply(context.getOldProjectFilePath())
            : new ArrayList<>();
        
        // 过滤掉不需要级联的文件
        List<FileMetadata> filteredMeta = dependentFilesWithMeta.stream()
            .filter(meta -> dependentFiles.contains(meta.getProjectFilePath()))
            .collect(Collectors.toList());
        
        for (FileMetadata fileMeta : filteredMeta) {
            if (context.getSender() != null && context.getSender().getSendEvent() != null) {
                CodeChangeEvent event = new CodeChangeEvent();
                event.setEventId(UUID.randomUUID().toString());
                event.setProjectName(context.getProjectName());
                
                // 级联变更无法获取绝对路径，设为 null
                // 后续通过 Git 信息拉取代码获得绝对路径
                event.setAbsoluteFilePath(null);
                
                // 设置 Git 信息
                event.setGitRepoUrl(fileMeta.getGitRepoUrl());
                event.setGitBranch(fileMeta.getGitBranch());
                
                event.setClasspathEntries(context.getClasspathEntries());
                event.setSourcepathEntries(context.getSourcepathEntries());
                event.setOldFileIdentifier(fileMeta.getProjectFilePath());
                event.setChangeType(ChangeType.CASCADE_UPDATE);
                event.setLanguage("java");
                event.setTimestamp(LocalDateTime.now());
                event.setReason("Cascade update from " + context.getOldProjectFilePath());
                
                context.getSender().getSendEvent().accept(event);
                log.debug("已发送级联变更事件: file={}, gitRepo={}, gitBranch={}", 
                    fileMeta.getProjectFilePath(), fileMeta.getGitRepoUrl(), fileMeta.getGitBranch());
            } else {
                log.warn("未配置事件发送器，忽略级联变更: {}", fileMeta.getProjectFilePath());
            }
        }
    }
    
}