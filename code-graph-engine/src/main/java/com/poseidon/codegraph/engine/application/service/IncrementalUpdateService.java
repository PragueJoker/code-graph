package com.poseidon.codegraph.engine.application.service;

import com.poseidon.codegraph.engine.application.converter.CodeGraphConverter;
import com.poseidon.codegraph.engine.application.repository.CodeGraphRepository;
import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import com.poseidon.codegraph.engine.domain.service.CodeGraphService;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * 增量更新服务（应用层）
 * 职责：
 * 1. 构建变更上下文（Data + Reader/Writer）
 * 2. 调用领域服务
 */
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
    public void handleFileChange(String projectRoot, String filePath, 
                                              String[] classpathEntries, String[] sourcepathEntries,
                                              boolean isCascade) {
        CodeGraphContext context = buildContext(projectRoot, classpathEntries, sourcepathEntries);
        
        if (isCascade) {
            context.setChangeType(ChangeType.CASCADE_UPDATE);
            context.setOldFilePath(filePath);
        } else {
            context.setChangeType(ChangeType.SOURCE_MODIFIED); // 默认为修改，或者需要更细粒度的入参
            context.setOldFilePath(filePath);
            context.setNewFilePath(filePath);
        }
        
        codeGraphService.handle(context);
    }
    
    /**
     * 处理文件新增
     */
    public void handleFileAdded(String projectRoot, String filePath,
                                             String[] classpathEntries, String[] sourcepathEntries) {
        CodeGraphContext context = buildContext(projectRoot, classpathEntries, sourcepathEntries);
        context.setChangeType(ChangeType.SOURCE_ADDED);
        context.setOldFilePath(null);
        context.setNewFilePath(filePath);
        
        codeGraphService.handle(context);
    }
    
    /**
     * 处理文件删除
     */
    public void handleFileDeleted(String projectRoot, String filePath,
                                               String[] classpathEntries, String[] sourcepathEntries) {
        CodeGraphContext context = buildContext(projectRoot, classpathEntries, sourcepathEntries);
        context.setChangeType(ChangeType.SOURCE_DELETED);
        context.setOldFilePath(filePath);
        context.setNewFilePath(null);
        
        codeGraphService.handle(context);
    }
    
    /**
     * 处理文件修改
     */
    public void handleFileModified(String projectRoot, String filePath,
                                                String[] classpathEntries, String[] sourcepathEntries) {
        CodeGraphContext context = buildContext(projectRoot, classpathEntries, sourcepathEntries);
        context.setChangeType(ChangeType.SOURCE_MODIFIED);
        context.setOldFilePath(filePath);
        context.setNewFilePath(filePath);
        
        codeGraphService.handle(context);
    }
    
    /**
     * 构建上下文（注入 Repository 实现）
     */
    private CodeGraphContext buildContext(String projectRoot, String[] classpathEntries, String[] sourcepathEntries) {
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectRoot(projectRoot);
        context.setClasspathEntries(classpathEntries);
        context.setSourcepathEntries(sourcepathEntries);
        
        // ========== 查询函数 (Reader) ==========
        
        context.getReader().setFindWhoCallsMe(filePath -> 
            repository.findWhoCallsMe(filePath)
        );
        
        context.getReader().setFindUnitsByFilePath(filePath -> 
            repository.findUnitsByFilePath(filePath).stream()
                .map(CodeGraphConverter::toDomain)
                .collect(Collectors.toList())
        );
        
        context.getReader().setFindFunctionsByFilePath(filePath -> 
            repository.findFunctionsByFilePath(filePath).stream()
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
        
        // ========== 修改函数 (Writer) ==========
        
        context.getWriter().setDeleteFileOutgoingCalls(filePath -> 
            repository.deleteFileOutgoingCalls(filePath)
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
        
        context.getWriter().setSaveCallRelationship(relationship -> 
            repository.saveCallRelationship(CodeGraphConverter.toDO(relationship))
        );
        
        // ========== 批量保存函数 ==========
        
        context.getWriter().setSavePackagesBatch(packages -> 
            repository.savePackagesBatch(
                packages.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setSaveUnitsBatch(units -> 
            repository.saveUnitsBatch(
                units.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setSaveFunctionsBatch(functions -> 
            repository.saveFunctionsBatch(
                functions.stream()
                    .map(CodeGraphConverter::toDO)
                    .collect(Collectors.toList())
            )
        );
        
        context.getWriter().setSaveCallRelationshipsBatch(relationships -> 
            repository.saveCallRelationshipsBatch(
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
                event.getProjectRoot(),
                event.getOldFileIdentifier(), // 级联变更的目标文件
                event.getClasspathEntries(),
                event.getSourcepathEntries(),
                true // isCascade = true
            );
        });
        
        return context;
    }
}
