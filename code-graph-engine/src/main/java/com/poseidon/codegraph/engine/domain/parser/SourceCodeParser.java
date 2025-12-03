package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.model.CallRelationship;
import com.poseidon.codegraph.engine.domain.model.CodeFunction;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import com.poseidon.codegraph.engine.domain.model.CodePackage;
import com.poseidon.codegraph.engine.domain.model.CodeUnit;

import java.util.List;

/**
 * 源码解析器接口
 * 定义源码解析的通用能力，支持全量解析和按类型解析
 */
public interface SourceCodeParser {
    
    /**
     * 解析源文件，返回完整的代码图谱
     * @param filePath 文件路径
     * @return 解析出的代码图谱片段
     */
    CodeGraph parse(String filePath);

    /**
     * 仅解析包（package 声明）
     * @param filePath 文件路径
     * @return 包列表（通常一个文件只有一个包）
     */
    List<CodePackage> parsePackages(String filePath);

    /**
     * 仅解析单元（类、接口、枚举等定义）
     * @param filePath 文件路径
     * @return 单元列表
     */
    List<CodeUnit> parseUnits(String filePath);

    /**
     * 仅解析函数（方法定义）
     * @param filePath 文件路径
     * @return 函数列表
     */
    List<CodeFunction> parseFunctions(String filePath);

    /**
     * 仅解析调用关系
     * @param filePath 文件路径
     * @return 调用关系列表
     */
    List<CallRelationship> parseRelationships(String filePath);
}
