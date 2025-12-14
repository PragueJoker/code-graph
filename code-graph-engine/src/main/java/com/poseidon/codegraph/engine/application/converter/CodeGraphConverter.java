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
    
    public static CodeEndpoint toDomain(CodeEndpointDO dobj) {
        if (dobj == null) return null;
        
        CodeEndpoint domain = new CodeEndpoint();
        domain.setId(dobj.getId());
        domain.setName(dobj.getName());
        domain.setQualifiedName(dobj.getQualifiedName());
        domain.setProjectFilePath(dobj.getProjectFilePath());
        domain.setGitRepoUrl(dobj.getGitRepoUrl());
        domain.setGitBranch(dobj.getGitBranch());
        domain.setLanguage(dobj.getLanguage());
        domain.setStartLine(dobj.getStartLine());
        domain.setEndLine(dobj.getEndLine());
        domain.setEndpointType(dobj.getEndpointType());
        domain.setDirection(dobj.getDirection());
        domain.setIsExternal(dobj.getIsExternal());
        domain.setHttpMethod(dobj.getHttpMethod());
        domain.setPath(dobj.getPath());
        domain.setNormalizedPath(dobj.getNormalizedPath());
        domain.setTopic(dobj.getTopic());
        domain.setOperation(dobj.getOperation());
        domain.setKeyPattern(dobj.getKeyPattern());
        domain.setDataStructure(dobj.getDataStructure());
        domain.setTableName(dobj.getTableName());
        domain.setDbOperation(dobj.getDbOperation());
        domain.setServiceName(dobj.getServiceName());
        domain.setParseLevel(dobj.getParseLevel());
        domain.setTargetService(dobj.getTargetService());
        domain.setFunctionId(dobj.getFunctionId());
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
    
    public static CodeEndpointDO toDO(CodeEndpoint domain) {
        if (domain == null) return null;
        
        CodeEndpointDO dobj = new CodeEndpointDO();
        dobj.setId(domain.getId());
        dobj.setName(domain.getName());
        dobj.setQualifiedName(domain.getQualifiedName());
        dobj.setProjectFilePath(domain.getProjectFilePath());
        dobj.setGitRepoUrl(domain.getGitRepoUrl());
        dobj.setGitBranch(domain.getGitBranch());
        dobj.setLanguage(domain.getLanguage());
        dobj.setStartLine(domain.getStartLine());
        dobj.setEndLine(domain.getEndLine());
        dobj.setEndpointType(domain.getEndpointType());
        dobj.setDirection(domain.getDirection());
        dobj.setIsExternal(domain.getIsExternal());
        dobj.setHttpMethod(domain.getHttpMethod());
        dobj.setPath(domain.getPath());
        dobj.setNormalizedPath(domain.getNormalizedPath());
        dobj.setTopic(domain.getTopic());
        dobj.setOperation(domain.getOperation());
        dobj.setKeyPattern(domain.getKeyPattern());
        dobj.setDataStructure(domain.getDataStructure());
        dobj.setTableName(domain.getTableName());
        dobj.setDbOperation(domain.getDbOperation());
        dobj.setServiceName(domain.getServiceName());
        dobj.setParseLevel(domain.getParseLevel());
        dobj.setTargetService(domain.getTargetService());
        dobj.setFunctionId(domain.getFunctionId());
        return dobj;
    }
}