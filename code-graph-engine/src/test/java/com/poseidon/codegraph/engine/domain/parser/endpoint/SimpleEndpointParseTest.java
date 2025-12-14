package com.poseidon.codegraph.engine.domain.parser.endpoint;

import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 简单的端点解析测试
 * 用于调试问题
 */
class SimpleEndpointParseTest {
    
    @Test
    void testParseSpringController() {
        // 1. 准备源代码
        String sourceCode = """
            package com.example.controller;
            
            import org.springframework.web.bind.annotation.*;
            
            @RestController
            @RequestMapping("/api/users")
            public class UserController {
                
                @GetMapping("/{id}")
                public User getUser(@PathVariable Long id) {
                    return null;
                }
                
                @PostMapping
                public User createUser(@RequestBody User user) {
                    return null;
                }
            }
            """;
        
        // 2. 解析 AST
        CompilationUnit cu = parseSource(sourceCode);
        
        // 3. 打印 AST 信息
        System.out.println("\n========== AST 信息 ==========");
        System.out.println("类型数量: " + cu.types().size());
        
        if (!cu.types().isEmpty()) {
            TypeDeclaration typeDecl = (TypeDeclaration) cu.types().get(0);
            System.out.println("类名: " + typeDecl.getName());
            System.out.println("修饰符数量: " + typeDecl.modifiers().size());
            
            // 打印类注解
            System.out.println("\n类注解:");
            for (Object modifier : typeDecl.modifiers()) {
                if (modifier instanceof Annotation) {
                    Annotation ann = (Annotation) modifier;
                    System.out.println("  - @" + ann.getTypeName());
                }
            }
            
            // 打印方法
            System.out.println("\n方法列表:");
            for (MethodDeclaration method : typeDecl.getMethods()) {
                System.out.println("  方法: " + method.getName());
                System.out.println("    修饰符数量: " + method.modifiers().size());
                
                // 打印方法注解
                for (Object modifier : method.modifiers()) {
                    if (modifier instanceof Annotation) {
                        Annotation ann = (Annotation) modifier;
                        System.out.println("    - @" + ann.getTypeName());
                        
                        // 打印注解值
                        if (ann instanceof SingleMemberAnnotation) {
                            Expression value = ((SingleMemberAnnotation) ann).getValue();
                            System.out.println("      value = " + value);
                        }
                    }
                }
            }
        }
        
        // 4. 调用端点解析服务
        System.out.println("\n========== 调用端点解析服务 ==========");
        EndpointParsingService service = new EndpointParsingService();
        service.init();
        
        // 打印加载的规则信息
        System.out.println("\n========== 加载的规则检查 ==========");
        var loader = new com.poseidon.codegraph.engine.domain.parser.endpoint.epr.loader.EprRuleLoader();
        var rules = loader.loadAllRules();
        for (var rule : rules) {
            System.out.println("规则: " + rule.getName());
            System.out.println("  enabled: " + rule.getEnabled());
            System.out.println("  priority: " + rule.getPriority());
            System.out.println("  scope: " + rule.getScope());
            if (rule.getScope() != null) {
                System.out.println("    packageIncludes: " + rule.getScope().getPackageIncludes());
                System.out.println("    packageExcludes: " + rule.getScope().getPackageExcludes());
            }
            System.out.println("  locate: " + rule.getLocate());
            System.out.println("  extract: " + (rule.getExtract() != null ? rule.getExtract().keySet() : null));
            System.out.println();
        }
        
        List<CodeEndpoint> endpoints = service.parseEndpoints(
            cu,
            "com.example.controller",
            "UserController.java",
            "/test/UserController.java"
        );
        
        System.out.println("\n========== 解析结果 ==========");
        System.out.println("解析到的端点数: " + endpoints.size());
        
        for (CodeEndpoint endpoint : endpoints) {
            System.out.println(String.format("  %s %s",
                endpoint.getHttpMethod(),
                endpoint.getPath()
            ));
        }
    }
    
    private CompilationUnit parseSource(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        parser.setUnitName("UserController.java");
        
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, "21");
        options.put(JavaCore.COMPILER_SOURCE, "21");
        parser.setCompilerOptions(options);
        
        return (CompilationUnit) parser.createAST(null);
    }
}

