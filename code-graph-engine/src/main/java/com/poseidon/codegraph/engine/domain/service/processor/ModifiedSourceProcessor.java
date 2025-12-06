package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeFunction;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import com.poseidon.codegraph.engine.domain.model.CodeUnit;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 源码修改处理器
 */
@Slf4j
public class ModifiedSourceProcessor extends AbstractChangeProcessor {
    
    @Override
    public boolean support(CodeGraphContext context) {
        return context.getChangeType() == ChangeType.SOURCE_MODIFIED;
    }
    
    @Override
    public void handle(CodeGraphContext context) {
        String oldProjectFilePath = context.getOldProjectFilePath();
        String newProjectFilePath = context.getNewProjectFilePath();
        String absoluteFilePath = context.getAbsoluteFilePath();
        
        log.info("处理修改文件: {}", newProjectFilePath);
        
        // 步骤 1：查找谁依赖我（入边）
        List<String> dependentFiles = context.getReader().getFindWhoCallsMe().apply(oldProjectFilePath);
        
        // 排除自己，避免不必要的自我级联更新（自身更新由当前 Processor 处理）
        if (dependentFiles != null) {
            dependentFiles.remove(oldProjectFilePath);
        }
        
        log.debug("找到依赖文件: {} 个", dependentFiles.size());
        
        // 步骤 2：触发级联变更（依赖我的文件）
        triggerCascadeChanges(context, dependentFiles);
        
        // 步骤 3：删除该文件的旧节点
        List<CodeUnit> oldUnits = context.getReader().getFindUnitsByProjectFilePath().apply(oldProjectFilePath);
        List<CodeFunction> oldFunctions = context.getReader().getFindFunctionsByProjectFilePath().apply(oldProjectFilePath);
        
        deleteNodes(oldUnits, oldFunctions, context);
        log.debug("删除旧节点: {} 个", oldUnits.size() + oldFunctions.size());
        
        // 步骤 4：解析新文件
        // 注意：这里我们使用 absoluteFilePath 来读取文件内容，使用 newProjectFilePath 作为节点标识
        CodeGraph newGraph = parseFile(context, absoluteFilePath, newProjectFilePath);
        log.debug("解析新文件: {} 个类, {} 个方法", newGraph.getUnitsAsList().size(), newGraph.getFunctionsAsList().size());
        
        // 步骤 5：保存新节点
        saveNodes(newGraph, context);
        log.debug("保存新节点完成");
        
        // 步骤 6：重建当前文件的调用关系
        int relationshipCount = rebuildFileCallRelationships(context, absoluteFilePath, newProjectFilePath, newGraph);
        log.debug("重建当前文件调用关系: {} 条", relationshipCount);
    }
}