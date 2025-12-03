package com.poseidon.codegraph.engine.domain.service.processor;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.event.ChangeType;
import lombok.extern.slf4j.Slf4j;

/**
 * 级联更新处理器
 */
@Slf4j
public class CascadeUpdateProcessor extends AbstractChangeProcessor {
    
    @Override
    public boolean support(CodeGraphContext context) {
        return context.getChangeType() == ChangeType.CASCADE_UPDATE;
    }
    
    @Override
    public void handle(CodeGraphContext context) {
        String filePath = context.getOldFilePath();
        log.info("处理级联变更: {}", filePath);
        
        // 步骤 1：删除该文件的出边
        context.getWriter().getDeleteFileOutgoingCalls().accept(filePath);
        log.debug("删除出边完成");
        
        // 步骤 2：重建调用关系
        int count = rebuildFileCallRelationships(context, filePath, null);
        log.debug("重建调用关系: {} 条", count);
    }
}

