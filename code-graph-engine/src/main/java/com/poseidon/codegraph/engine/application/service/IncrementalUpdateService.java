package com.poseidon.codegraph.engine.application.service;

import com.poseidon.codegraph.engine.application.converter.CodeGraphConverter;
import com.poseidon.codegraph.engine.application.repository.*;
import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import com.poseidon.codegraph.engine.domain.parser.enricher.GraphEnricher;
import com.poseidon.codegraph.engine.domain.service.CodeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 增量更新服务（应用层）
 * 职责：
 * 1. 构建变更上下文（Data + Reader/Writer）
 * 2. 调用领域服务
 */
@Slf4j
@Service
public class IncrementalUpdateService {
    
    private final CodeGraphService codeGraphService;
    private final CodePackageRepository packageRepository;
    private final CodeUnitRepository unitRepository;
    private final CodeFunctionRepository functionRepository;
    private final CodeRelationshipRepository relationshipRepository;
    private final CodeEndpointRepository endpointRepository;
    private final List<GraphEnricher> enrichers;
    
    public IncrementalUpdateService(
            CodePackageRepository packageRepository,
            CodeUnitRepository unitRepository,
            CodeFunctionRepository functionRepository,
            CodeRelationshipRepository relationshipRepository,
            CodeEndpointRepository endpointRepository,
            List<GraphEnricher> enrichers) {
        this.codeGraphService = new CodeGraphService();
        this.packageRepository = packageRepository;
        this.unitRepository = unitRepository;
        this.functionRepository = functionRepository;
        this.relationshipRepository = relationshipRepository;
        this.endpointRepository = endpointRepository;
        this.enrichers = enrichers;
        
        log.info("IncrementalUpdateService 初始化完成，已注入 {} 个增强器", 
            enrichers != null ? enrichers.size() : 0);
    }
    
    /**
     * 处理文件变更（通用入口）
     */
    public void handleFileChange(String projectName, String absoluteFilePath, String projectFilePath,
                                 String gitRepoUrl, String gitBranch,
                                 String[] classpathEntries, String[] sourcepathEntries,
                                 boolean isCascade) {
        CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch, classpathEntries, sourcepathEntries);
        
        if (isCascade) {
            context.setChangeType(ChangeType.CASCADE_UPDATE);
            // 级联更新时，传入的 projectFilePath 是触发变更的文件（可能是旧文件）
            context.setOldProjectFilePath(projectFilePath);
            // 级联变更通常是由于其他文件变更导致的，当前文件本身可能没有位置变化，所以 NewProjectFilePath 也可以设为 projectFilePath
            // 但具体逻辑取决于级联更新的处理方式，这里暂时只设置 OldProjectFilePath 作为标识
        } else {
            // 默认视为修改，或者需要调用方明确传入 ChangeType
            // 但在此简化方法中，暂且设为 MODIFIED，如果是新增/删除应该走专用方法
            context.setChangeType(ChangeType.SOURCE_MODIFIED); 
            context.setOldProjectFilePath(projectFilePath);
            context.setNewProjectFilePath(projectFilePath);
        }
        
        codeGraphService.handle(context);
    }
    
    /**
     * 处理文件新增
     */
    public void handleFileAdded(String projectName, String absoluteFilePath, String projectFilePath,
                                String gitRepoUrl, String gitBranch,
                                String[] classpathEntries, String[] sourcepathEntries) {
        log.info("处理文件新增: absolutePath={}, projectPath={}, classpathCount={}", absoluteFilePath, projectFilePath,
                 classpathEntries != null ? classpathEntries.length : 0);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch, classpathEntries, sourcepathEntries);
            context.setChangeType(ChangeType.SOURCE_ADDED);
            context.setOldProjectFilePath(null);
            context.setNewProjectFilePath(projectFilePath);
            
            codeGraphService.handle(context);
            log.info("文件新增处理完成: projectPath={}", projectFilePath);
        } catch (Exception e) {
            log.error("文件新增处理失败: projectPath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("处理文件新增失败: " + projectFilePath, e);
        }
    }
    
    /**
     * 处理文件删除
     */
    public void handleFileDeleted(String projectName, String absoluteFilePath, String projectFilePath,
                                  String gitRepoUrl, String gitBranch,
                                  String[] classpathEntries, String[] sourcepathEntries) {
        log.info("处理文件删除: absolutePath={}, projectPath={}", absoluteFilePath, projectFilePath);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch, classpathEntries, sourcepathEntries);
            context.setChangeType(ChangeType.SOURCE_DELETED);
            context.setOldProjectFilePath(projectFilePath);
            context.setNewProjectFilePath(null);
            
            codeGraphService.handle(context);
            log.info("文件删除处理完成: projectPath={}", projectFilePath);
        } catch (Exception e) {
            log.error("文件删除处理失败: projectPath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("处理文件删除失败: " + projectFilePath, e);
        }
    }
    
    /**
     * 处理文件修改
     */
    public void handleFileModified(String projectName, String absoluteFilePath, String projectFilePath,
                                   String gitRepoUrl, String gitBranch,
                                   String[] classpathEntries, String[] sourcepathEntries) {
        log.info("处理文件修改: absolutePath={}, projectPath={}, classpathCount={}", absoluteFilePath, projectFilePath,
                 classpathEntries != null ? classpathEntries.length : 0);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch, classpathEntries, sourcepathEntries);
            context.setChangeType(ChangeType.SOURCE_MODIFIED);
            context.setOldProjectFilePath(projectFilePath);
            context.setNewProjectFilePath(projectFilePath);
            
            codeGraphService.handle(context);
            log.info("文件修改处理完成: projectPath={}", projectFilePath);
        } catch (Exception e) {
            log.error("文件修改处理失败: projectPath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("处理文件修改失败: " + projectFilePath, e);
        }
    }
    
    /**
     * 构建上下文（注入 Repository 实现）
     */
    private CodeGraphContext buildContext(String projectName, String absoluteFilePath, String projectFilePath,
                                          String gitRepoUrl, String gitBranch,
                                          String[] classpathEntries, String[] sourcepathEntries) {
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName(projectName);
        context.setAbsoluteFilePath(absoluteFilePath);
        context.setProjectFilePath(projectFilePath);
        context.setGitRepoUrl(gitRepoUrl);
        context.setGitBranch(gitBranch);
        context.setClasspathEntries(classpathEntries);
        context.setSourcepathEntries(sourcepathEntries);
        context.setEnrichers(enrichers);
        
        // ========== 查询函数 (Reader) ==========
        
        context.getReader().setFindWhoCallsMe(path -> 
            relationshipRepository.findWhoCallsMe(path)
        );
        
        context.getReader().setFindWhoCallsMeWithMeta(path -> 
            relationshipRepository.findWhoCallsMeWithMeta(path).stream()
                .map(this::fileMetaInfoToMetadata)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindUnitsByProjectFilePath(path -> 
            unitRepository.findUnitsByProjectFilePath(path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindFunctionsByProjectFilePath(path -> 
            functionRepository.findFunctionsByProjectFilePath(path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindExistingFunctionsByQualifiedNames(qualifiedNames -> 
            functionRepository.findExistingFunctionsByQualifiedNames(qualifiedNames)
        );
        
        context.getReader().setFindExistingUnitsByQualifiedNames(qualifiedNames -> 
            unitRepository.findExistingUnitsByQualifiedNames(qualifiedNames)
        );
        
        context.getReader().setFindExistingPackagesByQualifiedNames(qualifiedNames -> 
            packageRepository.findExistingPackagesByQualifiedNames(qualifiedNames)
        );
        
        context.getReader().setFindExistingStructureRelationships(relationships -> 
            relationshipRepository.findExistingStructureRelationships(relationships)
        );
        
        context.getReader().setFindExistingEndpointsByIds(ids -> 
            endpointRepository.findExistingEndpointsByIds(ids)
        );
        
        context.getReader().setFindEndpointsByProjectFilePath(path -> 
            endpointRepository.findEndpointsByProjectFilePath(path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        // ========== 修改函数 (Writer) ==========
        
        context.getWriter().setDeleteFileOutgoingCalls(path -> 
            relationshipRepository.deleteFileOutgoingCalls(path)
        );
        
        context.getWriter().setDeleteNode(nodeId -> 
            // 使用 FunctionRepository 的删除方法（实际上 Neo4j 实现是通用的）
            // 或者使用 UnitRepository，效果一样。
            functionRepository.deleteById(nodeId)
        );
        
        // ========== 批量插入函数 ==========
        
        context.getWriter().setInsertPackagesBatch(packages -> 
            packageRepository.insertPackagesBatch(
                packages.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertUnitsBatch(units -> 
            unitRepository.insertUnitsBatch(
                units.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertFunctionsBatch(functions -> 
            functionRepository.insertFunctionsBatch(
                functions.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertRelationshipsBatch(relationships -> 
            relationshipRepository.insertRelationshipsBatch(
                relationships.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        // ========== 批量更新函数 ==========
        
        context.getWriter().setUpdatePackagesBatch(packages -> 
            packageRepository.updatePackagesBatch(
                packages.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateUnitsBatch(units -> 
            unitRepository.updateUnitsBatch(
                units.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateFunctionsBatch(functions -> 
            functionRepository.updateFunctionsBatch(
                functions.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertEndpointsBatch(endpoints -> 
            endpointRepository.insertEndpointsBatch(
                endpoints.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateEndpointsBatch(endpoints -> 
            endpointRepository.updateEndpointsBatch(
                endpoints.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        // ========== 事件发送 ==========
        
        context.getSender().setSendEvent(event -> {
            // 收到代码变更事件（CASCADE） -> 重新调用 handleFileChange
            // 注意：这里是同步调用，实际生产环境建议发送 MQ 消息或 Spring Event
            this.handleFileChange(
                event.getProjectName(),
                event.getAbsoluteFilePath(),
                event.getOldFileIdentifier(), // 级联变更的目标文件
                event.getGitRepoUrl(),
                event.getGitBranch(),
                event.getClasspathEntries(),
                event.getSourcepathEntries(),
                true // isCascade = true
            );
        });
        
        return context;
    }
    
    /**
     * 将应用层的 FileMetaInfo 转换为领域层的 FileMetadata
     */
    private com.poseidon.codegraph.engine.domain.model.FileMetadata fileMetaInfoToMetadata(
            com.poseidon.codegraph.engine.application.model.FileMetaInfo metaInfo) {
        com.poseidon.codegraph.engine.domain.model.FileMetadata metadata = 
            new com.poseidon.codegraph.engine.domain.model.FileMetadata();
        metadata.setProjectFilePath(metaInfo.getProjectFilePath());
        metadata.setGitRepoUrl(metaInfo.getGitRepoUrl());
        metadata.setGitBranch(metaInfo.getGitBranch());
        return metadata;
    }
}