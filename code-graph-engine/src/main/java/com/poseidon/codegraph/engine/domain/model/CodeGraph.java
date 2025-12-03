package com.poseidon.codegraph.engine.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 代码图（解析结果容器）
 * 用于存放单次解析的结果（可以是单个文件、多个文件或整个项目）
 */
@Data
public class CodeGraph {
    /**
     * 包列表
     */
    private List<CodePackage> packages = new ArrayList<>();
    
    /**
     * 代码单元列表
     */
    private List<CodeUnit> units = new ArrayList<>();
    
    /**
     * 函数列表
     */
    private List<CodeFunction> functions = new ArrayList<>();
    
    /**
     * 调用关系列表
     */
    private List<CallRelationship> relationships = new ArrayList<>();
    
    /**
     * 添加包
     */
    public void addPackage(CodePackage pkg) {
        if (this.packages == null) {
            this.packages = new ArrayList<>();
        }
        this.packages.add(pkg);
    }
    
    /**
     * 添加代码单元
     */
    public void addUnit(CodeUnit unit) {
        if (this.units == null) {
            this.units = new ArrayList<>();
        }
        this.units.add(unit);
    }
    
    /**
     * 添加函数
     */
    public void addFunction(CodeFunction function) {
        if (this.functions == null) {
            this.functions = new ArrayList<>();
        }
        this.functions.add(function);
    }
    
    /**
     * 添加调用关系
     */
    public void addRelationship(CallRelationship relationship) {
        if (this.relationships == null) {
            this.relationships = new ArrayList<>();
        }
        this.relationships.add(relationship);
    }
    
    /**
     * 获取包列表
     */
    public List<CodePackage> getPackagesAsList() {
        return this.packages != null ? this.packages : new ArrayList<>();
    }
    
    /**
     * 获取代码单元列表
     */
    public List<CodeUnit> getUnitsAsList() {
        return this.units != null ? this.units : new ArrayList<>();
    }
    
    /**
     * 获取函数列表
     */
    public List<CodeFunction> getFunctionsAsList() {
        return this.functions != null ? this.functions : new ArrayList<>();
    }
    
    /**
     * 获取调用关系列表
     */
    public List<CallRelationship> getRelationshipsAsList() {
        return this.relationships != null ? this.relationships : new ArrayList<>();
    }
}

