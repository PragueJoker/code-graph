package com.poseidon.codegraph.engine.application.converter;

import com.poseidon.codegraph.engine.application.model.*;
import com.poseidon.codegraph.engine.domain.model.*;

/**
 * 代码图谱转换器
 * 负责 DO（应用层）和领域模型（领域层）之间的转换
 */
public class CodeGraphConverter {
    
    // ========== DO -> 领域模型 ==========
    
    public static CodePackage toDomain(CodePackageDO dobj) {
        if (dobj == null) return null;
        
        CodePackage domain = new CodePackage();
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setPackagePath(dobj.getPackagePath());
        return domain;
    }
    
    public static CodeUnit toDomain(CodeUnitDO dobj) {
        if (dobj == null) return null;
        
        CodeUnit domain = new CodeUnit();
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setStartLine(dobj.getStartLine());
        domain.setEndLine(dobj.getEndLine());
        domain.setUnitType(dobj.getUnitType());
        domain.setModifiers(dobj.getModifiers());
        domain.setIsAbstract(dobj.getIsAbstract());
        domain.setPackageId(dobj.getPackageId());
        return domain;
    }
    
    public static CodeFunction toDomain(CodeFunctionDO dobj) {
        if (dobj == null) return null;
        
        CodeFunction domain = new CodeFunction();
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setLanguage(dobj.getLanguage());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setStartLine(dobj.getStartLine());
        domain.setEndLine(dobj.getEndLine());
        domain.setSignature(dobj.getSignature());
        domain.setReturnType(dobj.getReturnType());
        domain.setModifiers(dobj.getModifiers());
        domain.setIsStatic(dobj.getIsStatic());
        domain.setIsAsync(dobj.getIsAsync());
        domain.setIsConstructor(dobj.getIsConstructor());
        domain.setIsPlaceholder(dobj.getIsPlaceholder());
        return domain;
    }
    
    public static CodeRelationship toDomain(CodeRelationshipDO dobj) {
        if (dobj == null) return null;
        
        CodeRelationship domain = new CodeRelationship();
        domain.setId(dobj.getId());
        
        // 优先使用通用字段
        if (dobj.getFromNodeId() != null) {
            domain.setFromNodeId(dobj.getFromNodeId());
        } else if (dobj.getFromFunctionId() != null) {
            domain.setFromNodeId(dobj.getFromFunctionId());
        }
        
        if (dobj.getToNodeId() != null) {
            domain.setToNodeId(dobj.getToNodeId());
        } else if (dobj.getToFunctionId() != null) {
            domain.setToNodeId(dobj.getToFunctionId());
        }
        
        // 兼容性字段
        domain.setFromFunctionId(dobj.getFromFunctionId());
        domain.setToFunctionId(dobj.getToFunctionId());
        
        // 关系类型
        if (dobj.getRelationshipType() != null) {
            domain.setRelationshipType(RelationshipType.valueOf(dobj.getRelationshipType()));
        }
        
        domain.setLineNumber(dobj.getLineNumber());
        domain.setCallType(dobj.getCallType());
        domain.setLanguage(dobj.getLanguage());
        return domain;
    }
    
    // ========== 领域模型 -> DO ==========
    
    public static CodePackageDO toDO(CodePackage domain) {
        if (domain == null) return null;
        
        CodePackageDO dobj = new CodePackageDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setPackagePath(domain.getPackagePath());
        return dobj;
    }
    
    public static CodeUnitDO toDO(CodeUnit domain) {
        if (domain == null) return null;
        
        CodeUnitDO dobj = new CodeUnitDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setStartLine(domain.getStartLine());
        dobj.setEndLine(domain.getEndLine());
        dobj.setUnitType(domain.getUnitType());
        dobj.setModifiers(domain.getModifiers());
        dobj.setIsAbstract(domain.getIsAbstract());
        dobj.setPackageId(domain.getPackageId());
        return dobj;
    }
    
    public static CodeFunctionDO toDO(CodeFunction domain) {
        if (domain == null) return null;
        
        CodeFunctionDO dobj = new CodeFunctionDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setLanguage(domain.getLanguage());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setStartLine(domain.getStartLine());
        dobj.setEndLine(domain.getEndLine());
        dobj.setSignature(domain.getSignature());
        dobj.setReturnType(domain.getReturnType());
        dobj.setModifiers(domain.getModifiers());
        dobj.setIsStatic(domain.getIsStatic());
        dobj.setIsAsync(domain.getIsAsync());
        dobj.setIsConstructor(domain.getIsConstructor());
        dobj.setIsPlaceholder(domain.getIsPlaceholder());
        return dobj;
    }
    
    public static CodeRelationshipDO toDO(CodeRelationship domain) {
        if (domain == null) return null;
        
        CodeRelationshipDO dobj = new CodeRelationshipDO();
        dobj.setId(domain.getId());
        
        // 通用字段
        dobj.setFromNodeId(domain.getFromNodeId());
        dobj.setToNodeId(domain.getToNodeId());
        
        // 兼容性字段
        dobj.setFromFunctionId(domain.getFromFunctionId());
        dobj.setToFunctionId(domain.getToFunctionId());
        
        // 关系类型
        if (domain.getRelationshipType() != null) {
            dobj.setRelationshipType(domain.getRelationshipType().name());
        }
        
        dobj.setLineNumber(domain.getLineNumber());
        dobj.setCallType(domain.getCallType());
        dobj.setLanguage(domain.getLanguage());
        return dobj;
    }
}