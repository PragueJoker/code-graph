package com.poseidon.codegraph.engine.domain.parser;

import com.poseidon.codegraph.engine.domain.model.*;
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
            String source = Files.readString(Path.of(filePath));
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setSource(source.toCharArray());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            
            if (classpathEntries != null && classpathEntries.length > 0) {
                parser.setResolveBindings(true);
                parser.setBindingsRecovery(true);
                parser.setEnvironment(
                    classpathEntries,
                    sourcepathEntries != null ? sourcepathEntries : new String[0],
                    null,
                    true
                );
                parser.setUnitName(filePath);
            } else {
                parser.setResolveBindings(false);
            }
            
            return (CompilationUnit) parser.createAST(null);
        } catch (IOException e) {
            throw new RuntimeException("Failed to parse file: " + filePath, e);
        }
    }
    
    @Override
    public CodeGraph parse(String filePath) {
        CodeGraph graph = new CodeGraph();
        CompilationUnit cu = createAST(filePath);
        
        // 1. 解析 Package
        List<CodePackage> packages = parsePackages(cu, filePath);
        packages.forEach(graph::addPackage);
        
        // 2. 解析 Units 和 Functions
        List<CodeUnit> units = parseUnits(cu, filePath);
        for (CodeUnit unit : units) {
            graph.addUnit(unit);
            unit.getFunctions().forEach(graph::addFunction);
        }
        
        // 3. 解析调用关系
        List<CallRelationship> relationships = parseRelationships(cu, filePath);
        relationships.forEach(graph::addRelationship);
        
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
        if (binding != null) {
            unit.setQualifiedName(binding.getQualifiedName());
        } else {
            unit.setQualifiedName(packageName + "." + unit.getName());
        }
        
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
        if (binding != null) {
            unit.setQualifiedName(binding.getQualifiedName());
        } else {
            unit.setQualifiedName(packageName + "." + unit.getName());
        }
        
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
        if (binding != null) {
            unit.setQualifiedName(binding.getQualifiedName());
        } else {
            unit.setQualifiedName(packageName + "." + unit.getName());
        }
        
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
        
        Type returnType = method.getReturnType2();
        if (returnType != null) {
            function.setReturnType(returnType.toString());
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
                IMethodBinding targetBinding = node.resolveMethodBinding();
                if (targetBinding != null) {
                    ASTNode current = node.getParent();
                    while (current != null && !(current instanceof MethodDeclaration)) {
                        current = current.getParent();
                    }
                    
                    if (current instanceof MethodDeclaration) {
                        MethodDeclaration callerMethod = (MethodDeclaration) current;
                        IMethodBinding callerBinding = callerMethod.resolveBinding();
                        
                        if (callerBinding != null) {
                            CallRelationship rel = new CallRelationship();
                            rel.setId(UUID.randomUUID().toString());
                            
                            String callerQualifiedName = buildQualifiedName(callerBinding);
                            rel.setFromFunctionId(callerQualifiedName);
                            
                            String targetQualifiedName = buildQualifiedName(targetBinding);
                            rel.setToFunctionId(targetQualifiedName);
                            
                            rel.setLineNumber(cu.getLineNumber(node.getStartPosition()));
                            rel.setCallType(Modifier.isStatic(targetBinding.getModifiers()) ? "static" : "virtual");
                            rel.setLanguage("java");
                            
                            relationships.add(rel);
                        }
                    }
                }
                return true;
            }
        });
        
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
    
    private String buildMethodSignature(MethodDeclaration method) {
        StringBuilder sig = new StringBuilder(method.getName().getIdentifier()).append("(");
        
        List<?> params = method.parameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) sig.append(", ");
            SingleVariableDeclaration param = (SingleVariableDeclaration) params.get(i);
            sig.append(param.getType().toString());
        }
        
        sig.append("):");
        
        Type returnType = method.getReturnType2();
        if (returnType != null) {
            sig.append(returnType.toString());
        } else {
            sig.append("void");
        }
        
        return sig.toString();
    }
    
    private String buildQualifiedName(IMethodBinding binding) {
        StringBuilder sb = new StringBuilder();
        sb.append(binding.getDeclaringClass().getQualifiedName());
        sb.append(".");
        sb.append(binding.getName());
        sb.append("(");
        ITypeBinding[] params = binding.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(params[i].getQualifiedName());
        }
        sb.append(")");
        sb.append(":");
        ITypeBinding returnType = binding.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getQualifiedName());
        } else {
            sb.append("void");
        }
        return sb.toString();
    }
}
