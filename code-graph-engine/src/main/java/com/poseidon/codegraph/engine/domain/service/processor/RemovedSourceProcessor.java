package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeFunction;
import com.poseidon.codegraph.engine.domain.model.CodeUnit;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 源码删除处理器
 */
@Slf4j
public class RemovedSourceProcessor extends AbstractChangeProcessor {
    
    @Override
    public boolean support(CodeGraphContext context) {
        return context.getChangeType() == ChangeType.SOURCE_DELETED;
    }
    
    @Override
    public void handle(CodeGraphContext context) {
        String projectFilePath = context.getOldProjectFilePath();
        log.info("处理删除文件: {}", projectFilePath);
        
        // 步骤 1：查找谁依赖我
        List<String> dependentFiles = context.getReader().getFindWhoCallsMe().apply(projectFilePath);
        log.debug("找到依赖文件: {} 个", dependentFiles.size());
        
        // 步骤 2：查找该文件的所有节点
        List<CodeUnit> units = context.getReader().getFindUnitsByProjectFilePath().apply(projectFilePath);
        List<CodeFunction> fileFunctions = context.getReader().getFindFunctionsByProjectFilePath().apply(projectFilePath);
        
        // 步骤 3：删除所有节点（会自动删除所有相关的边）
        deleteNodes(units, fileFunctions, context);
        log.debug("删除节点: {} 个", units.size() + fileFunctions.size());
        
        // 步骤 4：触发级联变更
        triggerCascadeChanges(context, dependentFiles);
    }
}