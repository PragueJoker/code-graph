package com.poseidon.codegraph.engine.application.model;

import lombok.Data;
import java.util.List;

/**
 * 代码函数数据对象（应用层）
 */
@Data
public class CodeFunctionDO {
    private String id;
    private String name;
    private String qualifiedName;
    private String language;
    private String projectFilePath;
    private Integer startLine;
    private Integer endLine;
    private String signature;
    private String returnType;
    private List<String> modifiers;
    private Boolean isStatic;
    private Boolean isAsync;
    private Boolean isConstructor;
    private Boolean isPlaceholder;
}

