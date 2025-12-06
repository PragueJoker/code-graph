package com.poseidon.codegraph.engine.domain.context;

import com.poseidon.codegraph.engine.domain.model.CodeFunction;
import com.poseidon.codegraph.engine.domain.model.CodeUnit;
import lombok.Data;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 图谱读取器
 * 聚合所有查询相关的函数
 */
@Data
public class GraphReader {
    
    /**
     * 查找谁依赖我（入边）
     * Input: projectFilePath -> Output: List<dependentProjectFilePath>
     */
    private Function<String, List<String>> findWhoCallsMe;
    
    /**
     * 查找文件的所有代码单元
     * Input: projectFilePath -> Output: List<CodeUnit>
     */
    private Function<String, List<CodeUnit>> findUnitsByProjectFilePath;
    
    /**
     * 查找文件的所有函数
     * Input: projectFilePath -> Output: List<CodeFunction>
     */
    private Function<String, List<CodeFunction>> findFunctionsByProjectFilePath;
    
    /**
     * 批量查询函数是否存在
     * Input: List<qualifiedName> -> Output: Set<existingQualifiedName>
     */
    private Function<java.util.List<String>, java.util.Set<String>> findExistingFunctionsByQualifiedNames;
    
    /**
     * 批量查询单元是否存在
     * Input: List<qualifiedName> -> Output: Set<existingQualifiedName>
     */
    private Function<java.util.List<String>, java.util.Set<String>> findExistingUnitsByQualifiedNames;
    
    /**
     * 批量查询包是否存在
     * Input: List<qualifiedName> -> Output: Set<existingQualifiedName>
     */
    private Function<java.util.List<String>, java.util.Set<String>> findExistingPackagesByQualifiedNames;
}