package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.model.CodeRelationship;
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
     * @param absoluteFilePath 文件绝对路径（用于读取）
     * @param projectName 项目名称（用于生成唯一 ID）
     * @param projectFilePath 项目相对路径（用于节点属性）
     * @param gitRepoUrl Git 仓库 URL
     * @param gitBranch Git 分支名
     * @return 解析出的代码图谱片段
     */
    CodeGraph parse(String absoluteFilePath, String projectName, String projectFilePath, 
                    String gitRepoUrl, String gitBranch);

    /**
     * 仅解析包（package 声明）
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 包列表（通常一个文件只有一个包）
     */
    List<CodePackage> parsePackages(String absoluteFilePath, String projectName, String projectFilePath);

    /**
     * 仅解析单元（类、接口、枚举等定义）
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 单元列表
     */
    List<CodeUnit> parseUnits(String absoluteFilePath, String projectName, String projectFilePath);

    /**
     * 仅解析函数（方法定义）
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 函数列表
     */
    List<CodeFunction> parseFunctions(String absoluteFilePath, String projectName, String projectFilePath);

    /**
     * 仅解析关系（包括调用关系、结构关系等）
     * @param absoluteFilePath 文件绝对路径
     * @param projectName 项目名称
     * @param projectFilePath 项目相对路径
     * @return 关系列表
     */
    List<CodeRelationship> parseRelationships(String absoluteFilePath, String projectName, String projectFilePath);
}