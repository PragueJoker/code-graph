package com.poseidon.codegraph.engine.domain.service;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.service.processor.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码图谱领域服务
 * 职责：变更处理的分发器
 * 
 * public: 领域服务入口
 */
@Slf4j
public class CodeGraphService {
    
    private final List<CodeChangeProcessor> processors = new ArrayList<>();
    
    public CodeGraphService() {
        // 注册所有处理器
        processors.add(new CascadeUpdateProcessor());
        processors.add(new NewSourceProcessor());
        processors.add(new RemovedSourceProcessor());
        processors.add(new ModifiedSourceProcessor());
    }
    
    /**
     * 处理文件变更
     */
    public void handle(CodeGraphContext context) {
        for (CodeChangeProcessor processor : processors) {
            if (processor.support(context)) {
                processor.handle(context);
                return;
            }
        }
        
        log.warn("未找到支持的处理器: {}", context);
        throw new IllegalArgumentException("No processor found for context: " + context);
    }
}
