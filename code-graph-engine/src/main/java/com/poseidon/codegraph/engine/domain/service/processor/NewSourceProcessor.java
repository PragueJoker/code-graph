package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import lombok.extern.slf4j.Slf4j;

/**
 * 源码新增处理器
 */
@Slf4j
public class NewSourceProcessor extends AbstractChangeProcessor {
    
    @Override
    public boolean support(CodeGraphContext context) {
        return context.getChangeType() == ChangeType.SOURCE_ADDED;
    }
    
    @Override
    public void handle(CodeGraphContext context) {
        String filePath = context.getNewFilePath();
        log.info("处理新增文件: {}", filePath);
        
        // 步骤 1：解析文件
        CodeGraph graph = parseFile(context, filePath);
        log.debug("解析完成: {} 个类, {} 个方法", graph.getUnitsAsList().size(), graph.getFunctionsAsList().size());
        
        // 步骤 2：保存节点
        saveNodes(graph, context);
        log.debug("保存节点完成");
        
        // 步骤 3：建立调用关系
        int relationshipCount = rebuildFileCallRelationships(context, filePath, graph);
        log.debug("建立调用关系: {} 条", relationshipCount);
    }
}

