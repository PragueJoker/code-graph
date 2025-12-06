package com.poseidon.codegraph.engine.application.repository;

import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;

import java.util.List;

/**
 * 代码关系仓储接口
 */
public interface CodeRelationshipRepository {

    /**
     * 查找谁依赖我（入边）
     * 
     * @param targetProjectFilePath 目标文件路径
     * @return 依赖该文件的文件路径列表
     */
    List<String> findWhoCallsMe(String targetProjectFilePath);

    /**
     * 删除文件的出边（该文件发起的调用）
     * 
     * @param projectFilePath 文件路径
     */
    void deleteFileOutgoingCalls(String projectFilePath);

    /**
     * 批量插入关系（包括调用关系、结构关系等）（纯数据库操作，不做存在性检查）
     */
    void insertRelationshipsBatch(List<CodeRelationshipDO> relationships);
}

