package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.engine.domain.model.CallRelationship;
import com.poseidon.codegraph.engine.domain.model.CodeFunction;
import com.poseidon.codegraph.engine.domain.model.CodePackage;
import com.poseidon.codegraph.engine.domain.model.CodeUnit;
import lombok.Data;

import java.util.function.Consumer;

/**
 * 图谱写入器
 * 聚合所有增删改相关的函数
 */
@Data
public class GraphWriter {
    
    // ========== 删除函数 ==========
    
    /**
     * 删除文件的出边
     * Input: filePath
     */
    private Consumer<String> deleteFileOutgoingCalls;
    
    /**
     * 删除节点
     * Input: nodeId
     */
    private Consumer<String> deleteNode;
    
    // ========== 保存函数 ==========
    
    /**
     * 保存代码包
     */
    private Consumer<CodePackage> savePackage;
    
    /**
     * 保存代码单元
     */
    private Consumer<CodeUnit> saveUnit;
    
    /**
     * 保存函数
     */
    private Consumer<CodeFunction> saveFunction;
    
    /**
     * 保存调用关系
     */
    private Consumer<CallRelationship> saveCallRelationship;
    
    // ========== 批量保存函数 ==========
    
    /**
     * 批量保存函数（先查询是否存在，存在则更新，不存在则插入）
     */
    private Consumer<java.util.List<CodeFunction>> saveFunctionsBatch;
    
    /**
     * 批量保存单元（先查询是否存在，存在则更新，不存在则插入）
     */
    private Consumer<java.util.List<CodeUnit>> saveUnitsBatch;
    
    /**
     * 批量保存包（先查询是否存在，存在则更新，不存在则插入）
     */
    private Consumer<java.util.List<CodePackage>> savePackagesBatch;
    
    /**
     * 批量保存调用关系
     */
    private Consumer<java.util.List<CallRelationship>> saveCallRelationshipsBatch;
}
