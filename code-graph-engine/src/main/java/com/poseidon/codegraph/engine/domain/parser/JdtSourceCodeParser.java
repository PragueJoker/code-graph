package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 基于 JDT 的源码解析器实现
 * 
 * 职责：
 * 1. 管理项目上下文（projectRoot, classpath, sourcepath）
 * 2. 创建 JDT AST (CompilationUnit)
 * 3. 解析代码单元、函数、调用关系等
 */
@Slf4j
public class JdtSourceCodeParser implements SourceCodeParser {
    
    private final String projectRoot;
    private final String[] classpathEntries;
    private final String[] sourcepathEntries;
    
    public JdtSourceCodeParser(String projectRoot, String[] classpathEntries, String[] sourcepathEntries) {
        this.projectRoot = projectRoot;
        this.classpathEntries = classpathEntries;
        this.sourcepathEntries = sourcepathEntries;
    }
    
    /**
     * 创建 AST (CompilationUnit)
     */
    private CompilationUnit createAST(String filePath) {
        try {
            log.debug("开始解析文件: {}", filePath);
            String source = Files.readString(Path.of(filePath));
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(source.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            
            // 构建完整的 classpath：用户提供的 classpath + JDK（用于类型绑定解析）
            String[] fullClasspath = buildFullClasspath();
            
            if (fullClasspath.length > 0) {
                log.debug("启用绑定解析: classpathCount={}, sourcepathCount={}", 
                          fullClasspath.length, 
                          sourcepathEntries != null ? sourcepathEntries.length : 0);
                parser.setResolveBindings(true);
                parser.setBindingsRecovery(true);
                parser.setEnvironment(
                    fullClasspath,
                    sourcepathEntries != null ? sourcepathEntries : new String[0],
                    null,
                    true  // includeRunningVMBootclasspath: true 表示包含运行时的 bootclasspath（JDK）
                );
                parser.setUnitName(filePath);
            } else {
                log.warn("classpath 为空，禁用绑定解析: file={}, 这将导致类型绑定失败，数据可能不准确", filePath);
                parser.setResolveBindings(false);
            }
            
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            log.debug("文件解析完成: {}", filePath);
            return cu;
        } catch (IOException e) {
            log.error("文件读取失败: file={}, error={}", filePath, e.getMessage());
            throw new RuntimeException("Failed to parse file: " + filePath, e);
        } catch (Exception e) {
            log.error("AST 创建失败: file={}, error={}", filePath, e.getMessage());
            throw new RuntimeException("Failed to create AST for file: " + filePath, e);
        }
    }
    
    /**
     * 构建完整的 classpath：用户提供的 classpath + JDK
     * JDK 只用于类型绑定解析，不会解析成节点
     */
    private String[] buildFullClasspath() {
        List<String> fullClasspath = new ArrayList<>();
        
        // 1. 添加用户提供的 classpath
        if (classpathEntries != null) {
            for (String entry : classpathEntries) {
                if (entry != null && !entry.isEmpty()) {
                    fullClasspath.add(entry);
                }
            }
        }
        
        // 2. 添加 JDK（用于类型绑定解析）
        // 注意：JDK 只用于类型绑定解析（如 String -> java.lang.String），不会创建 JDK 的节点
        // 因为 parseUnits/parseFunctions 只遍历当前文件的 AST，不会遍历 JDK 的类
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            // Java 9+ 使用模块系统，JDK 类通过 jrt-fs 访问
            // setEnvironment 的 includeRunningVMBootclasspath=true 应该会自动包含
            // 但如果绑定解析仍然失败，可以尝试添加 JDK 的 lib 目录
            Path jdkLibPath = Path.of(javaHome, "lib");
            if (jdkLibPath.toFile().exists()) {
                // 对于 Java 9+，JDK 类在模块系统中，不需要手动添加
                // 对于 Java 8，可以添加 rt.jar，但 setEnvironment 应该已经处理了
            }
        }
        
        return fullClasspath.toArray(new String[0]);
    }
    
    @Override
    public CodeGraph parse(String filePath) {
        log.info("开始解析代码图谱: file={}", filePath);
        CodeGraph graph = new CodeGraph();
        CompilationUnit cu = createAST(filePath);
        
        // 1. 解析 Package
        List<CodePackage> packages = parsePackages(cu, filePath);
        packages.forEach(graph::addPackage);
        log.debug("解析 Package 完成: file={}, packageCount={}", filePath, packages.size());
        
        // 2. 解析 Units 和 Functions
        List<CodeUnit> units = parseUnits(cu, filePath);
        int functionCount = 0;
        for (CodeUnit unit : units) {
            graph.addUnit(unit);
            unit.getFunctions().forEach(graph::addFunction);
            functionCount += unit.getFunctions().size();
        }
        log.info("解析 Units 和 Functions 完成: file={}, unitCount={}, functionCount={}", 
                 filePath, units.size(), functionCount);
        
        // 3. 解析调用关系
        List<CallRelationship> relationships = parseRelationships(cu, filePath);
        relationships.forEach(graph::addRelationship);
        
        log.info("代码图谱解析完成: file={}, packages={}, units={}, functions={}, relationships={}", 
                 filePath, packages.size(), units.size(), functionCount, relationships.size());
        return graph;
    }
    
    @Override
    public List<CodePackage> parsePackages(String filePath) {
        CompilationUnit cu = createAST(filePath);
        return parsePackages(cu, filePath);
    }
    
    @Override
    public List<CodeUnit> parseUnits(String filePath) {
        CompilationUnit cu = createAST(filePath);
        return parseUnits(cu, filePath);
    }
    
    @Override
    public List<CodeFunction> parseFunctions(String filePath) {
        CompilationUnit cu = createAST(filePath);
        List<CodeUnit> units = parseUnits(cu, filePath);
        List<CodeFunction> functions = new ArrayList<>();
        for (CodeUnit unit : units) {
            functions.addAll(unit.getFunctions());
        }
        return functions;
    }
    
    @Override
    public List<CallRelationship> parseRelationships(String filePath) {
        CompilationUnit cu = createAST(filePath);
        return parseRelationships(cu, filePath);
    }
    
    // ========== 内部解析方法 ==========
    
    private List<CodePackage> parsePackages(CompilationUnit cu, String filePath) {
        List<CodePackage> packages = new ArrayList<>();
        String packageName = extractPackageName(cu);
        if (packageName != null && !packageName.isEmpty()) {
            CodePackage pkg = new CodePackage();
            pkg.setId(packageName);
            pkg.setName(packageName);
            pkg.setQualifiedName(packageName);
            pkg.setPackagePath(packageName.replace('.', '/'));
            // Package 的 filePath 是包所在的目录路径
            String packageDir = Path.of(filePath).getParent().toString();
            pkg.setFilePath(toRelativePath(packageDir));
            pkg.setLanguage("java");
            packages.add(pkg);
        }
        return packages;
    }
    
    private List<CodeUnit> parseUnits(CompilationUnit cu, String filePath) {
        List<CodeUnit> units = new ArrayList<>();
        String packageName = extractPackageName(cu);
        
        for (Object type : cu.types()) {
            if (type instanceof TypeDeclaration) {
                units.add(parseTypeDeclaration((TypeDeclaration) type, filePath, packageName, cu));
            } else if (type instanceof EnumDeclaration) {
                units.add(parseEnumDeclaration((EnumDeclaration) type, filePath, packageName, cu));
            } else if (type instanceof AnnotationTypeDeclaration) {
                units.add(parseAnnotationTypeDeclaration((AnnotationTypeDeclaration) type, filePath, packageName, cu));
            }
        }
        return units;
    }
    
    private CodeUnit parseTypeDeclaration(TypeDeclaration typeDecl, String filePath, 
                                         String packageName, CompilationUnit cu) {
        CodeUnit unit = new CodeUnit();
        unit.setName(typeDecl.getName().getIdentifier());
        
        ITypeBinding binding = typeDecl.resolveBinding();
        if (binding == null) {
            log.error("类型绑定解析失败: class={}, package={}, 请检查 classpath 配置", 
                     unit.getName(), packageName);
            throw new RuntimeException("类型绑定解析失败: " + unit.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        unit.setQualifiedName(binding.getQualifiedName());
        
        unit.setId(unit.getQualifiedName());
        unit.setUnitType(typeDecl.isInterface() ? "interface" : "class");
        
        int modifiers = typeDecl.getModifiers();
        unit.setModifiers(extractModifiers(modifiers));
        unit.setIsAbstract(Modifier.isAbstract(modifiers));
        
        unit.setFilePath(toRelativePath(filePath));
        unit.setStartLine(cu.getLineNumber(typeDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(typeDecl.getStartPosition() + typeDecl.getLength()));
        unit.setLanguage("java");
        
        // 解析方法
        for (MethodDeclaration method : typeDecl.getMethods()) {
            CodeFunction function = parseMethodDeclaration(method, unit, cu);
            unit.addFunction(function);
        }
        
        return unit;
    }
    
    private CodeUnit parseEnumDeclaration(EnumDeclaration enumDecl, String filePath,
                                         String packageName, CompilationUnit cu) {
        CodeUnit unit = new CodeUnit();
        unit.setName(enumDecl.getName().getIdentifier());
        
        ITypeBinding binding = enumDecl.resolveBinding();
        if (binding == null) {
            log.error("枚举类型绑定解析失败: enum={}, package={}, 请检查 classpath 配置", 
                     unit.getName(), packageName);
            throw new RuntimeException("枚举类型绑定解析失败: " + unit.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        unit.setQualifiedName(binding.getQualifiedName());
        
        unit.setId(unit.getQualifiedName());
        unit.setUnitType("enum");
        unit.setModifiers(extractModifiers(enumDecl.getModifiers()));
        unit.setIsAbstract(false);
        unit.setFilePath(toRelativePath(filePath));
        unit.setStartLine(cu.getLineNumber(enumDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(enumDecl.getStartPosition() + enumDecl.getLength()));
        unit.setLanguage("java");
        
        return unit;
    }
    
    private CodeUnit parseAnnotationTypeDeclaration(AnnotationTypeDeclaration annoDecl, String filePath,
                                                   String packageName, CompilationUnit cu) {
        CodeUnit unit = new CodeUnit();
        unit.setName(annoDecl.getName().getIdentifier());
        
        ITypeBinding binding = annoDecl.resolveBinding();
        if (binding == null) {
            log.error("注解类型绑定解析失败: annotation={}, package={}, 请检查 classpath 配置", 
                     unit.getName(), packageName);
            throw new RuntimeException("注解类型绑定解析失败: " + unit.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        unit.setQualifiedName(binding.getQualifiedName());
        
        unit.setId(unit.getQualifiedName());
        unit.setUnitType("annotation");
        unit.setModifiers(extractModifiers(annoDecl.getModifiers()));
        unit.setIsAbstract(false);
        unit.setFilePath(toRelativePath(filePath));
        unit.setStartLine(cu.getLineNumber(annoDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(annoDecl.getStartPosition() + annoDecl.getLength()));
        unit.setLanguage("java");
        
        return unit;
    }
    
    private CodeFunction parseMethodDeclaration(MethodDeclaration method, CodeUnit unit, CompilationUnit cu) {
        CodeFunction function = new CodeFunction();
        function.setName(method.getName().getIdentifier());
        
        String signature = buildMethodSignature(method);
        function.setSignature(signature);
        
        String qualifiedName = unit.getQualifiedName() + "." + signature;
        function.setQualifiedName(qualifiedName);
        function.setId(qualifiedName);
        
        // 设置返回类型（使用完整类型名）
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            log.error("方法绑定解析失败，无法获取返回类型: method={}", function.getName());
            throw new RuntimeException("方法绑定解析失败: " + function.getName() + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        ITypeBinding returnTypeBinding = binding.getReturnType();
        if (returnTypeBinding != null) {
            function.setReturnType(getQualifiedTypeName(returnTypeBinding));
        } else {
            function.setReturnType("void");
        }
        
        int modifiers = method.getModifiers();
        function.setModifiers(extractModifiers(modifiers));
        function.setIsStatic(Modifier.isStatic(modifiers));
        function.setIsConstructor(method.isConstructor());
        function.setIsAsync(false);
        
        function.setFilePath(unit.getFilePath());
        function.setStartLine(cu.getLineNumber(method.getStartPosition()));
        function.setEndLine(cu.getLineNumber(method.getStartPosition() + method.getLength()));
        function.setLanguage("java");
        
        return function;
    }
    
    private List<CallRelationship> parseRelationships(CompilationUnit cu, String filePath) {
        List<CallRelationship> relationships = new ArrayList<>();
        
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                String methodName = node.getName().getIdentifier();
                int lineNumber = cu.getLineNumber(node.getStartPosition());
                
                IMethodBinding targetBinding = node.resolveMethodBinding();
                if (targetBinding == null) {
                    // 错误：被调用方法的绑定解析失败
                    log.error("方法调用的目标绑定解析失败: file={}, line={}, method={}, " +
                              "请检查 classpath 配置。", 
                              filePath, lineNumber, methodName);
                    throw new RuntimeException("方法调用的目标绑定解析失败: file=" + filePath + 
                        ", line=" + lineNumber + ", method=" + methodName + 
                        "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
                }
                
                ASTNode current = node.getParent();
                while (current != null && !(current instanceof MethodDeclaration)) {
                    current = current.getParent();
                }
                
                if (!(current instanceof MethodDeclaration)) {
                    // 错误：无法找到调用者方法声明
                    log.error("无法找到调用者方法声明: file={}, line={}, method={}, " +
                              "方法调用不在任何方法内部。请检查代码结构。", 
                              filePath, lineNumber, methodName);
                    throw new RuntimeException("无法找到调用者方法声明: file=" + filePath + 
                        ", line=" + lineNumber + ", method=" + methodName + 
                        "，方法调用不在任何方法内部。");
                }
                
                MethodDeclaration callerMethod = (MethodDeclaration) current;
                IMethodBinding callerBinding = callerMethod.resolveBinding();
                
                if (callerBinding == null) {
                    // 错误：调用者方法的绑定解析失败
                    String callerMethodName = callerMethod.getName().getIdentifier();
                    log.error("调用者方法绑定解析失败: file={}, line={}, callerMethod={}, targetMethod={}, " +
                              "请检查 classpath 配置。", 
                              filePath, lineNumber, callerMethodName, methodName);
                    throw new RuntimeException("调用者方法绑定解析失败: file=" + filePath + 
                        ", line=" + lineNumber + ", callerMethod=" + callerMethodName + 
                        ", targetMethod=" + methodName + 
                        "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
                }
                
                CallRelationship rel = new CallRelationship();
                rel.setId(UUID.randomUUID().toString());
                
                // 调用者和被调用者都使用完整类型名（避免类型歧义）
                String callerQualifiedName = buildQualifiedName(callerBinding);
                rel.setFromFunctionId(callerQualifiedName);
                
                String targetQualifiedName = buildQualifiedName(targetBinding);
                rel.setToFunctionId(targetQualifiedName);
                
                rel.setLineNumber(lineNumber);
                rel.setCallType(Modifier.isStatic(targetBinding.getModifiers()) ? "static" : "virtual");
                rel.setLanguage("java");
                
                relationships.add(rel);
                
                log.debug("解析调用关系成功: {}:{} -> {}", filePath, lineNumber, targetQualifiedName);
                return true;
            }
        });
        
        log.info("文件调用关系解析完成: file={}, relationshipCount={}", filePath, relationships.size());
        return relationships;
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 将绝对路径转换为相对于项目根的路径
     */
    private String toRelativePath(String absolutePath) {
        if (projectRoot == null || projectRoot.isEmpty()) {
            return absolutePath;
        }
        
        try {
            Path projectPath = Path.of(projectRoot).toAbsolutePath().normalize();
            Path filePath = Path.of(absolutePath).toAbsolutePath().normalize();
            
            if (filePath.startsWith(projectPath)) {
                Path relative = projectPath.relativize(filePath);
                return relative.toString().replace('\\', '/'); // 统一使用 / 分隔符
            }
            
            // 如果文件不在项目根目录下，返回原路径
            return absolutePath;
        } catch (Exception e) {
            // 如果转换失败，返回原路径
            return absolutePath;
        }
    }
    
    private String extractPackageName(CompilationUnit cu) {
        PackageDeclaration packageDecl = cu.getPackage();
        if (packageDecl != null) {
            return packageDecl.getName().getFullyQualifiedName();
        }
        return "(default)";
    }
    
    private List<String> extractModifiers(int modifiers) {
        List<String> result = new ArrayList<>();
        if (Modifier.isPublic(modifiers)) result.add("public");
        if (Modifier.isPrivate(modifiers)) result.add("private");
        if (Modifier.isProtected(modifiers)) result.add("protected");
        if (Modifier.isStatic(modifiers)) result.add("static");
        if (Modifier.isFinal(modifiers)) result.add("final");
        if (Modifier.isAbstract(modifiers)) result.add("abstract");
        if (Modifier.isSynchronized(modifiers)) result.add("synchronized");
        return result;
    }
    
    /**
     * 构建方法签名，使用完整类型名（避免类型歧义）
     * 例如：findUser(java.lang.String, java.lang.Integer):com.example.User
     * 
     * 注意：如果绑定解析失败，直接抛出异常，不使用 fallback 逻辑
     */
    private String buildMethodSignature(MethodDeclaration method) {
        String methodName = method.getName().getIdentifier();
        
        // 必须使用 IMethodBinding 获取完整类型名
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            log.error("方法绑定解析失败: method={}, 可能原因: classpath 不完整或 JDK 未正确配置。", methodName);
            throw new RuntimeException("方法绑定解析失败: " + methodName + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        
        return buildMethodSignatureFromBinding(binding);
    }
    
    /**
     * 从 IMethodBinding 构建方法签名（使用完整类型名）
     */
    private String buildMethodSignatureFromBinding(IMethodBinding binding) {
        StringBuilder sig = new StringBuilder(binding.getName()).append("(");
        
        ITypeBinding[] params = binding.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sig.append(", ");
            sig.append(getQualifiedTypeName(params[i]));
        }
        
        sig.append("):");
        
        ITypeBinding returnType = binding.getReturnType();
        if (returnType != null) {
            sig.append(getQualifiedTypeName(returnType));
        } else {
            sig.append("void");
        }
        
        return sig.toString();
    }
    
    /**
     * 构建方法的完整限定名（使用完整类型名）
     * 例如：com.example.UserService.findUser(java.lang.String, java.lang.Integer):com.example.User
     */
    private String buildQualifiedName(IMethodBinding binding) {
        StringBuilder sb = new StringBuilder();
        sb.append(binding.getDeclaringClass().getQualifiedName());
        sb.append(".");
        sb.append(buildMethodSignatureFromBinding(binding));
        return sb.toString();
    }
    
    /**
     * 获取类型的完整限定名（避免类型歧义）
     * 例如：java.lang.String -> java.lang.String
     *       java.util.List -> java.util.List
     *       int[] -> int[]
     */
    private String getQualifiedTypeName(ITypeBinding typeBinding) {
        if (typeBinding.isArray()) {
            // 数组类型：递归处理元素类型
            ITypeBinding elementType = typeBinding.getElementType();
            return getQualifiedTypeName(elementType) + "[]";
        } else if (typeBinding.isPrimitive()) {
            // 基本类型：直接返回名称
            return typeBinding.getName();
        } else {
            // 引用类型：返回完整限定名
            return typeBinding.getQualifiedName();
        }
    }
    
}
