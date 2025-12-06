package com.poseidon.codegraph.engine.application.service;

import com.poseidon.codegraph.engine.application.converter.CodeGraphConverter;
import com.poseidon.codegraph.engine.application.repository.CodeGraphRepository;
import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import com.poseidon.codegraph.engine.domain.service.CodeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final CodeGraphRepository repository;
    
    public IncrementalUpdateService(CodeGraphRepository repository) {
        this.codeGraphService = new CodeGraphService();  // 直接使用领域服务
        this.repository = repository;
    }
    
    /**
     * 处理文件变更（通用入口）
     */
    public void handleFileChange(String projectName, String absoluteFilePath, String projectFilePath,
                                 String[] classpathEntries, String[] sourcepathEntries,
                                 boolean isCascade) {
        CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, classpathEntries, sourcepathEntries);
        
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
                                String[] classpathEntries, String[] sourcepathEntries) {
        log.info("处理文件新增: absolutePath={}, projectPath={}, classpathCount={}", absoluteFilePath, projectFilePath,
                 classpathEntries != null ? classpathEntries.length : 0);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, classpathEntries, sourcepathEntries);
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
                                  String[] classpathEntries, String[] sourcepathEntries) {
        log.info("处理文件删除: absolutePath={}, projectPath={}", absoluteFilePath, projectFilePath);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, classpathEntries, sourcepathEntries);
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
                                   String[] classpathEntries, String[] sourcepathEntries) {
        log.info("处理文件修改: absolutePath={}, projectPath={}, classpathCount={}", absoluteFilePath, projectFilePath,
                 classpathEntries != null ? classpathEntries.length : 0);
        
        try {
            CodeGraphContext context = buildContext(projectName, absoluteFilePath, projectFilePath, classpathEntries, sourcepathEntries);
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
                                          String[] classpathEntries, String[] sourcepathEntries) {
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectName(projectName);
        context.setAbsoluteFilePath(absoluteFilePath);
        context.setProjectFilePath(projectFilePath);
        context.setClasspathEntries(classpathEntries);
        context.setSourcepathEntries(sourcepathEntries);
        
        // ========== 查询函数 (Reader) ==========
        
        context.getReader().setFindWhoCallsMe(path -> 
            repository.findWhoCallsMe(path)
        );
        
        context.getReader().setFindUnitsByProjectFilePath(path -> 
            repository.findUnitsByProjectFilePath(path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindFunctionsByProjectFilePath(path -> 
            repository.findFunctionsByProjectFilePath(path).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindFunctionByQualifiedName(qualifiedName -> 
            repository.findFunctionByQualifiedName(qualifiedName)
                .map(CodeGraphConverter::toDomain)
        );
        
        context.getReader().setFindExistingFunctionsByQualifiedNames(qualifiedNames -> 
            repository.findExistingFunctionsByQualifiedNames(qualifiedNames)
        );
        
        context.getReader().setFindExistingUnitsByQualifiedNames(qualifiedNames -> 
            repository.findExistingUnitsByQualifiedNames(qualifiedNames)
        );
        
        context.getReader().setFindExistingPackagesByQualifiedNames(qualifiedNames -> 
            repository.findExistingPackagesByQualifiedNames(qualifiedNames)
        );
        
        // ========== 修改函数 (Writer) ==========
        
        context.getWriter().setDeleteFileOutgoingCalls(path -> 
            repository.deleteFileOutgoingCalls(path)
        );
        
        context.getWriter().setDeleteNode(nodeId -> 
            repository.deleteNode(nodeId)
        );
        
        context.getWriter().setSavePackage(pkg -> 
            repository.savePackage(CodeGraphConverter.toDO(pkg))
        );
        
        context.getWriter().setSaveUnit(unit -> 
            repository.saveUnit(CodeGraphConverter.toDO(unit))
        );
        
        context.getWriter().setSaveFunction(function -> 
            repository.saveFunction(CodeGraphConverter.toDO(function))
        );
        
        context.getWriter().setSaveRelationship(relationship -> 
            repository.saveRelationship(CodeGraphConverter.toDO(relationship))
        );
        
        // ========== 批量插入函数 ==========
        
        context.getWriter().setInsertPackagesBatch(packages -> 
            repository.insertPackagesBatch(
                packages.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertUnitsBatch(units -> 
            repository.insertUnitsBatch(
                units.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertFunctionsBatch(functions -> 
            repository.insertFunctionsBatch(
                functions.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setInsertRelationshipsBatch(relationships -> 
            repository.insertRelationshipsBatch(
                relationships.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        // ========== 批量更新函数 ==========
        
        context.getWriter().setUpdatePackagesBatch(packages -> 
            repository.updatePackagesBatch(
                packages.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateUnitsBatch(units -> 
            repository.updateUnitsBatch(
                units.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateFunctionsBatch(functions -> 
            repository.updateFunctionsBatch(
                functions.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setUpdateRelationshipsBatch(relationships -> 
            repository.updateRelationshipsBatch(
                relationships.stream()
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
                event.getClasspathEntries(),
                event.getSourcepathEntries(),
                true // isCascade = true
            );
        });
        
        return context;
    }
}