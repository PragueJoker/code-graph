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
    
    // ========== 批量插入函数 ==========
    
    /**
     * 批量插入函数（纯数据库操作）
     */
    private Consumer<java.util.List<CodeFunction>> insertFunctionsBatch;
    
    /**
     * 批量插入单元（纯数据库操作）
     */
    private Consumer<java.util.List<CodeUnit>> insertUnitsBatch;
    
    /**
     * 批量插入包（纯数据库操作）
     */
    private Consumer<java.util.List<CodePackage>> insertPackagesBatch;
    
    /**
     * 批量插入调用关系（纯数据库操作）
     */
    private Consumer<java.util.List<CallRelationship>> insertCallRelationshipsBatch;
    
    // ========== 批量更新函数 ==========
    
    /**
     * 批量更新函数（纯数据库操作）
     */
    private Consumer<java.util.List<CodeFunction>> updateFunctionsBatch;
    
    /**
     * 批量更新单元（纯数据库操作）
     */
    private Consumer<java.util.List<CodeUnit>> updateUnitsBatch;
    
    /**
     * 批量更新包（纯数据库操作）
     */
    private Consumer<java.util.List<CodePackage>> updatePackagesBatch;
    
    /**
     * 批量更新调用关系（纯数据库操作）
     */
    private Consumer<java.util.List<CallRelationship>> updateCallRelationshipsBatch;
}
