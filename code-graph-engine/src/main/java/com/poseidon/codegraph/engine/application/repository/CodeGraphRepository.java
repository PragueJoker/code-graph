package com.poseidon.codegraph.engine.application.repository;

import com.poseidon.codegraph.engine.application.model.*;

import java.util.List;
import java.util.Optional;

/**
 * 代码图谱仓储接口（应用层定义）
 * 使用应用层的 DO 模型
 * 由基础设施层实现
 */
public interface CodeGraphRepository {
    
    // ========== 查询操作 ==========
    
    /**
     * 查找谁依赖我（入边）
     * 
     * @param targetProjectFilePath 目标文件路径
     * @return 依赖该文件的文件路径列表
     */
    List<String> findWhoCallsMe(String targetProjectFilePath);
    
    /**
     * 根据文件路径查找所有代码单元
     */
    List<CodeUnitDO> findUnitsByProjectFilePath(String projectFilePath);
    
    /**
     * 根据文件路径查找所有函数
     */
    List<CodeFunctionDO> findFunctionsByProjectFilePath(String projectFilePath);
    
    /**
     * 根据全限定名查找函数
     */
    Optional<CodeFunctionDO> findFunctionByQualifiedName(String qualifiedName);
    
    /**
     * 批量查询函数是否存在
     * @param qualifiedNames 全限定名列表
     * @return 存在的全限定名集合
     */
    java.util.Set<String> findExistingFunctionsByQualifiedNames(java.util.List<String> qualifiedNames);
    
    /**
     * 批量查询单元是否存在
     * @param qualifiedNames 全限定名列表
     * @return 存在的全限定名集合
     */
    java.util.Set<String> findExistingUnitsByQualifiedNames(java.util.List<String> qualifiedNames);
    
    /**
     * 批量查询包是否存在
     * @param qualifiedNames 全限定名列表
     * @return 存在的全限定名集合
     */
    java.util.Set<String> findExistingPackagesByQualifiedNames(java.util.List<String> qualifiedNames);
    
    // ========== 删除操作 ==========
    
    /**
     * 删除文件的出边（该文件发起的调用）
     * 
     * @param projectFilePath 文件路径
     */
    void deleteFileOutgoingCalls(String projectFilePath);
    
    /**
     * 删除节点（会自动删除所有相关的边）
     * 
     * @param nodeId 节点 ID（全限定名）
     */
    void deleteNode(String nodeId);
    
    // ========== 保存操作 ==========
    
    void savePackage(CodePackageDO pkg);
    
    void saveUnit(CodeUnitDO unit);
    
    void saveFunction(CodeFunctionDO function);
    
    void saveRelationship(CodeRelationshipDO relationship);
    
    // ========== 批量插入操作 ==========
    
    /**
     * 批量插入函数（纯数据库操作，不做存在性检查）
     */
    void insertFunctionsBatch(java.util.List<CodeFunctionDO> functions);
    
    /**
     * 批量插入单元（纯数据库操作，不做存在性检查）
     */
    void insertUnitsBatch(java.util.List<CodeUnitDO> units);
    
    /**
     * 批量插入包（纯数据库操作，不做存在性检查）
     */
    void insertPackagesBatch(java.util.List<CodePackageDO> packages);
    
    /**
     * 批量插入关系（包括调用关系、结构关系等）（纯数据库操作，不做存在性检查）
     */
    void insertRelationshipsBatch(java.util.List<CodeRelationshipDO> relationships);
    
    // ========== 批量更新操作 ==========
    
    /**
     * 批量更新函数（纯数据库操作，不做存在性检查）
     */
    void updateFunctionsBatch(java.util.List<CodeFunctionDO> functions);
    
    /**
     * 批量更新单元（纯数据库操作，不做存在性检查）
     */
    void updateUnitsBatch(java.util.List<CodeUnitDO> units);
    
    /**
     * 批量更新包（纯数据库操作，不做存在性检查）
     */
    void updatePackagesBatch(java.util.List<CodePackageDO> packages);
    
    /**
     * 批量更新关系（包括调用关系、结构关系等）（纯数据库操作，不做存在性检查）
     */
    void updateRelationshipsBatch(java.util.List<CodeRelationshipDO> relationships);
}

