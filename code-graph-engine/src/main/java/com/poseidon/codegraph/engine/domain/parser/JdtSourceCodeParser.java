package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.parser.enricher.GraphEnricher;
import com.poseidon.codegraph.engine.domain.parser.filter.FilterPipeline;
import com.poseidon.codegraph.engine.domain.parser.filter.GetterSetterFilter;
import com.poseidon.codegraph.engine.domain.parser.filter.PackageWhitelistFilter;
import com.poseidon.codegraph.engine.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;

/**
 * 基于 JDT 的源码解析器实现
 */
@Slf4j
public class JdtSourceCodeParser extends AbstractSourceCodeParser {
    
    private final String[] classpathEntries;
    private final String[] sourcepathEntries;
    
    // 司内包前缀白名单（只解析源码和这些包中的方法调用）
    private static final Set<String> INTERNAL_PACKAGE_PREFIXES = Set.of(
        "com.poseidon."
    );
    
    public JdtSourceCodeParser(String[] classpathEntries, String[] sourcepathEntries) {
        super(new FilterPipeline()
            .addFilter(new GetterSetterFilter())
            .addFilter(new PackageWhitelistFilter(INTERNAL_PACKAGE_PREFIXES)));
        this.classpathEntries = classpathEntries;
        this.sourcepathEntries = sourcepathEntries;
    }
    
    public JdtSourceCodeParser(String[] classpathEntries, String[] sourcepathEntries, 
                              List<GraphEnricher> enrichers) {
        super(new FilterPipeline()
            .addFilter(new GetterSetterFilter())
            .addFilter(new PackageWhitelistFilter(INTERNAL_PACKAGE_PREFIXES)),
            enrichers);
        this.classpathEntries = classpathEntries;
        this.sourcepathEntries = sourcepathEntries;
    }
    
    private CompilationUnit createAST(String absoluteFilePath) {
        try {
            log.debug("开始解析文件: {}", absoluteFilePath);
            String source = Files.readString(Path.of(absoluteFilePath));
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(source.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            
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
                    true
                );
                parser.setUnitName(absoluteFilePath);
            } else {
                log.warn("classpath 为空，禁用绑定解析: file={}, 这将导致类型绑定失败，数据可能不准确", absoluteFilePath);
                parser.setResolveBindings(false);
            }
            
            CompilationUnit cu = (CompilationUnit) parser.createAST(null);
            log.debug("文件解析完成: {}", absoluteFilePath);
            return cu;
        } catch (IOException e) {
            log.error("文件读取失败: file={}, error={}", absoluteFilePath, e.getMessage());
            throw new RuntimeException("Failed to parse file: " + absoluteFilePath, e);
        } catch (Exception e) {
            log.error("AST 创建失败: file={}, error={}", absoluteFilePath, e.getMessage());
            throw new RuntimeException("Failed to create AST for file: " + absoluteFilePath, e);
        }
    }
    
    private String[] buildFullClasspath() {
        List<String> fullClasspath = new ArrayList<>();
        if (classpathEntries != null) {
            for (String entry : classpathEntries) {
                if (entry != null && !entry.isEmpty()) {
                    fullClasspath.add(entry);
                }
            }
        }
        return fullClasspath.toArray(new String[0]);
    }
    
    @Override
    public CodeGraph parse(String absoluteFilePath, String projectName, String projectFilePath,
                          String gitRepoUrl, String gitBranch) {
        log.info("开始解析代码图谱: absoluteFile={}, projectFile={}, git={}/{}", 
                absoluteFilePath, projectFilePath, gitRepoUrl, gitBranch);
        CodeGraph graph = new CodeGraph();
        CompilationUnit cu = createAST(absoluteFilePath);
        
        // 提取包名
        String packageName = extractPackageName(cu);
        
        List<CodePackage> packages = parsePackages(cu, absoluteFilePath, projectName, projectFilePath);
        packages.forEach(pkg -> {
            pkg.setGitRepoUrl(gitRepoUrl);
            pkg.setGitBranch(gitBranch);
            graph.addPackage(pkg);
        });
        log.debug("解析 Package 完成: file={}, packageCount={}", projectFilePath, packages.size());
        
        List<CodeUnit> units = parseUnits(cu, absoluteFilePath, projectName, projectFilePath);
        int functionCount = 0;
        String packageId = packages.isEmpty() ? null : packages.get(0).getId();
        
        for (CodeUnit unit : units) {
            if (packageId != null) {
                unit.setPackageId(packageId);
            }
            unit.setGitRepoUrl(gitRepoUrl);
            unit.setGitBranch(gitBranch);
            graph.addUnit(unit);
            
            for (CodeFunction function : unit.getFunctions()) {
                function.setGitRepoUrl(gitRepoUrl);
                function.setGitBranch(gitBranch);
                graph.addFunction(function);
            }
            functionCount += unit.getFunctions().size();
        }
        log.info("解析 Units 和 Functions 完成: file={}, unitCount={}, functionCount={}", 
                 projectFilePath, units.size(), functionCount);
        
        buildBelongsToRelationships(graph, packages, units);
        
        List<CodeRelationship> relationships = parseRelationships(cu, projectName, projectFilePath);
        relationships.forEach(graph::addRelationship);
        
        log.info("代码图谱解析完成: file={}, packages={}, units={}, functions={}, relationships={}", 
                 projectFilePath, packages.size(), units.size(), functionCount, graph.getRelationshipsAsList().size());
        
        // 应用增强器（端点解析等）
        CodeGraphContext context = buildEnrichmentContext(absoluteFilePath, projectName, 
                                                          projectFilePath, gitRepoUrl, gitBranch, packageName);
        applyEnrichers(graph, cu, context);
        
        return graph;
    }
    
    /**
     * 构建增强上下文
     */
    private CodeGraphContext buildEnrichmentContext(String absoluteFilePath, String projectName,
                                                     String projectFilePath, String gitRepoUrl,
                                                     String gitBranch, String packageName) {
        CodeGraphContext context = new CodeGraphContext();
        context.setAbsoluteFilePath(absoluteFilePath);
        context.setProjectName(projectName);
        context.setProjectFilePath(projectFilePath);
        context.setGitRepoUrl(gitRepoUrl);
        context.setGitBranch(gitBranch);
        context.setPackageName(packageName);
        return context;
    }
    
    @Override
    public List<CodePackage> parsePackages(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        return parsePackages(cu, absoluteFilePath, projectName, projectFilePath);
    }
    
    @Override
    public List<CodeUnit> parseUnits(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        return parseUnits(cu, absoluteFilePath, projectName, projectFilePath);
    }
    
    @Override
    public List<CodeFunction> parseFunctions(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        List<CodeUnit> units = parseUnits(cu, absoluteFilePath, projectName, projectFilePath);
        List<CodeFunction> functions = new ArrayList<>();
        for (CodeUnit unit : units) {
            functions.addAll(unit.getFunctions());
        }
        return functions;
    }
    
    @Override
    public List<CodeRelationship> parseRelationships(String absoluteFilePath, String projectName, String projectFilePath) {
        CompilationUnit cu = createAST(absoluteFilePath);
        return parseRelationships(cu, projectName, projectFilePath);
    }
    
    private List<CodePackage> parsePackages(CompilationUnit cu, String absoluteFilePath, String projectName, String projectFilePath) {
        List<CodePackage> packages = new ArrayList<>();
        String packageName = extractPackageName(cu);
        if (packageName != null && !packageName.isEmpty()) {
            CodePackage pkg = new CodePackage();
            pkg.setId(packageName);
            pkg.setName(packageName);
            pkg.setQualifiedName(packageName);
            pkg.setPackagePath(packageName.replace('.', '/'));
            
            Path path = Path.of(projectFilePath);
            String packageDir = path.getParent() != null ? path.getParent().toString() : "";
            pkg.setProjectFilePath(packageDir.replace('\\', '/'));
            pkg.setLanguage("java");
            packages.add(pkg);
        }
        return packages;
    }
    
    private List<CodeUnit> parseUnits(CompilationUnit cu, String absoluteFilePath, String projectName, String projectFilePath) {
        List<CodeUnit> units = new ArrayList<>();
        String packageName = extractPackageName(cu);
        
        for (Object type : cu.types()) {
            if (type instanceof TypeDeclaration) {
                units.add(parseTypeDeclaration((TypeDeclaration) type, projectName, projectFilePath, packageName, cu));
            } else if (type instanceof EnumDeclaration) {
                units.add(parseEnumDeclaration((EnumDeclaration) type, projectName, projectFilePath, packageName, cu));
            } else if (type instanceof AnnotationTypeDeclaration) {
                units.add(parseAnnotationTypeDeclaration((AnnotationTypeDeclaration) type, projectName, projectFilePath, packageName, cu));
            }
        }
        return units;
    }
    
    private CodeUnit parseTypeDeclaration(TypeDeclaration typeDecl, String projectName, String projectFilePath, 
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
        
        unit.setProjectFilePath(projectFilePath);
        unit.setStartLine(cu.getLineNumber(typeDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(typeDecl.getStartPosition() + typeDecl.getLength()));
        unit.setLanguage("java");
        
        for (MethodDeclaration method : typeDecl.getMethods()) {
            CodeFunction function = parseMethodDeclaration(method, unit, projectName, cu);
            unit.addFunction(function);
        }
        
        return unit;
    }
    
    private CodeUnit parseEnumDeclaration(EnumDeclaration enumDecl, String projectName, String projectFilePath,
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
        unit.setProjectFilePath(projectFilePath);
        unit.setStartLine(cu.getLineNumber(enumDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(enumDecl.getStartPosition() + enumDecl.getLength()));
        unit.setLanguage("java");
        
        return unit;
    }
    
    private CodeUnit parseAnnotationTypeDeclaration(AnnotationTypeDeclaration annoDecl, String projectName, String projectFilePath,
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
        unit.setProjectFilePath(projectFilePath);
        unit.setStartLine(cu.getLineNumber(annoDecl.getStartPosition()));
        unit.setEndLine(cu.getLineNumber(annoDecl.getStartPosition() + annoDecl.getLength()));
        unit.setLanguage("java");
        
        return unit;
    }
    
    private CodeFunction parseMethodDeclaration(MethodDeclaration method, CodeUnit unit, String projectName, CompilationUnit cu) {
        CodeFunction function = new CodeFunction();
        function.setName(method.getName().getIdentifier());
        
        String signature = buildMethodSignature(method);
        function.setSignature(signature);
        
        String qualifiedName = unit.getQualifiedName() + "." + signature;
        function.setQualifiedName(qualifiedName);
        function.setId(qualifiedName);
        
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
        
        function.setProjectFilePath(unit.getProjectFilePath());
        function.setStartLine(cu.getLineNumber(method.getStartPosition()));
        function.setEndLine(cu.getLineNumber(method.getStartPosition() + method.getLength()));
        function.setLanguage("java");
        
        return function;
    }
    
    private List<CodeRelationship> parseRelationships(CompilationUnit cu, String projectName, String projectFilePath) {
        List<CodeRelationship> relationships = new ArrayList<>();
        
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                String methodName = node.getName().getIdentifier();
                int lineNumber = cu.getLineNumber(node.getStartPosition());

                IMethodBinding targetBinding = node.resolveMethodBinding();
                if (targetBinding == null) {
                    log.debug("方法目标绑定失败（跳过）: file={}, line={}, method={}", 
                              projectFilePath, lineNumber, methodName);
                    return true;
                }
                
                // 硬编码过滤逻辑已移除，改用 PackageWhitelistFilter
                
                ASTNode current = node.getParent();
                while (current != null && !(current instanceof MethodDeclaration)) {
                    current = current.getParent();
                }
                
                if (!(current instanceof MethodDeclaration)) {
                    log.warn("无法找到调用者方法声明（跳过）: file={}, line={}, method={}", 
                              projectFilePath, lineNumber, methodName);
                    return true;
                }
                
                MethodDeclaration callerMethod = (MethodDeclaration) current;
                IMethodBinding callerBinding = callerMethod.resolveBinding();
                
                if (callerBinding == null) {
                    String callerMethodName = callerMethod.getName().getIdentifier();
                    log.warn("调用者绑定失败（跳过）: file={}, line={}, callerMethod={}", 
                              projectFilePath, lineNumber, callerMethodName);
                    return true;
                }
                
                CodeRelationship rel = new CodeRelationship();
                rel.setId(UUID.randomUUID().toString());
                
                String callerQualifiedName = buildQualifiedName(callerBinding);
                String fromNodeId = callerQualifiedName;
                rel.setFromNodeId(fromNodeId);
                
                String targetQualifiedName = buildQualifiedName(targetBinding);
                String toNodeId = targetQualifiedName;
                rel.setToNodeId(toNodeId);
                
                rel.setRelationshipType(RelationshipType.CALLS);
                rel.setLineNumber(lineNumber);
                rel.setCallType(Modifier.isStatic(targetBinding.getModifiers()) ? "static" : "virtual");
                rel.setLanguage("java");
                
                if (!shouldKeepRelationship(rel, targetBinding)) {
                    log.debug("过滤掉关系: {} -> {}", fromNodeId, toNodeId);
                    return true;
                }
                
                relationships.add(rel);
                
                log.debug("解析调用关系成功: {}:{} -> {}", projectFilePath, lineNumber, toNodeId);
                return true;
            }
        });
        
        log.info("文件调用关系解析完成: file={}, relationshipCount={}", projectFilePath, relationships.size());
        return relationships;
    }
    
    private void buildBelongsToRelationships(CodeGraph graph, List<CodePackage> packages, List<CodeUnit> units) {
        String packageId = packages.isEmpty() ? null : packages.get(0).getId();
        
        if (packageId != null) {
            for (CodeUnit unit : units) {
                CodeRelationship rel = new CodeRelationship();
                rel.setId(UUID.randomUUID().toString());
                rel.setFromNodeId(packageId);
                rel.setToNodeId(unit.getId());
                rel.setRelationshipType(RelationshipType.PACKAGE_TO_UNIT);
                rel.setLanguage("java");
                graph.addRelationship(rel);
                log.debug("构建结构关系: Package {} -> Unit {}", packageId, unit.getId());
            }
        }
        
        for (CodeUnit unit : units) {
            for (CodeFunction function : unit.getFunctions()) {
                CodeRelationship rel = new CodeRelationship();
                rel.setId(UUID.randomUUID().toString());
                rel.setFromNodeId(unit.getId());
                rel.setToNodeId(function.getId());
                rel.setRelationshipType(RelationshipType.UNIT_TO_FUNCTION);
                rel.setLanguage("java");
                graph.addRelationship(rel);
                log.debug("构建结构关系: Unit {} -> Function {}", unit.getId(), function.getId());
            }
        }
        
        log.info("构建结构关系完成: packageToUnit={}, unitToFunction={}", 
                 packageId != null ? units.size() : 0,
                 units.stream().mapToInt(u -> u.getFunctions().size()).sum());
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
    
    private String buildMethodSignature(MethodDeclaration method) {
        String methodName = method.getName().getIdentifier();
        IMethodBinding binding = method.resolveBinding();
        if (binding == null) {
            log.error("方法绑定解析失败: method={}, 可能原因: classpath 不完整或 JDK 未正确配置。", methodName);
            throw new RuntimeException("方法绑定解析失败: " + methodName + 
                "，请检查 classpath 配置是否包含所有必要的依赖和 JDK。");
        }
        return buildMethodSignatureFromBinding(binding);
    }
    
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
    
    private String buildQualifiedName(IMethodBinding binding) {
        StringBuilder sb = new StringBuilder();
        sb.append(binding.getDeclaringClass().getQualifiedName());
        sb.append(".");
        sb.append(buildMethodSignatureFromBinding(binding));
        return sb.toString();
    }
    
    private String getQualifiedTypeName(ITypeBinding typeBinding) {
        if (typeBinding.isArray()) {
            ITypeBinding elementType = typeBinding.getElementType();
            return getQualifiedTypeName(elementType) + "[]";
        } else if (typeBinding.isPrimitive()) {
            return typeBinding.getName();
        } else {
            // 使用擦除后的类型，避免泛型差异导致ID不一致
            return typeBinding.getErasure().getQualifiedName();
        }
    }
}