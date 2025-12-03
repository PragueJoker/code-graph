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
        String oldFilePath = context.getOldFilePath();
        String newFilePath = context.getNewFilePath();
        
        log.info("处理修改文件: {}", newFilePath);
        
        // 步骤 1：查找谁依赖我（入边）
        List<String> dependentFiles = context.getReader().getFindWhoCallsMe().apply(oldFilePath);
        log.debug("找到依赖文件: {} 个", dependentFiles.size());
        
        // 步骤 2：触发级联变更（依赖我的文件）
        triggerCascadeChanges(context, dependentFiles);
        
        // 步骤 3：删除该文件的旧节点
        List<CodeUnit> oldUnits = context.getReader().getFindUnitsByFilePath().apply(oldFilePath);
        List<CodeFunction> oldFunctions = context.getReader().getFindFunctionsByFilePath().apply(oldFilePath);
        
        deleteNodes(oldUnits, oldFunctions, context);
        log.debug("删除旧节点: {} 个", oldUnits.size() + oldFunctions.size());
        
        // 步骤 4：解析新文件
        CodeGraph newGraph = parseFile(context, newFilePath);
        log.debug("解析新文件: {} 个类, {} 个方法", newGraph.getUnitsAsList().size(), newGraph.getFunctionsAsList().size());
        
        // 步骤 5：保存新节点
        saveNodes(newGraph, context);
        log.debug("保存新节点完成");
        
        // 步骤 6：重建当前文件的调用关系
        int relationshipCount = rebuildFileCallRelationships(context, newFilePath, newGraph);
        log.debug("重建当前文件调用关系: {} 条", relationshipCount);
    }
}

