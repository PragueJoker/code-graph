package com.poseidon.codegraph.engine.domain.parser.endpoint;

import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端点解析服务测试
 * 测试多种框架的入口端点 path 提取
 */
class EndpointParsingServiceTest {
    
    private EndpointParsingService endpointParsingService;
    
    @BeforeEach
    void setUp() {
        endpointParsingService = new EndpointParsingService();
        endpointParsingService.init();
    }
    
    /**
     * 测试 Spring MVC Controller
     */
    @Test
    void testSpringMvcController() throws IOException {
        // 1. 读取测试源代码
        String sourceCode = readTestSource("SpringMvcController.java");
        
        // 2. 解析为 AST
        CompilationUnit cu = parseSource(sourceCode);
        
        // 3. 解析端点
        List<CodeEndpoint> endpoints = endpointParsingService.parseEndpoints(
            cu,
            "com.example.controller",
            "SpringMvcController.java",
            "/test/SpringMvcController.java"
        );
        
        // 4. 验证结果
        assertNotNull(endpoints);
        assertEquals(6, endpoints.size(), "应该解析出 6 个端点");
        
        // 验证 GET /api/users/{id}
        CodeEndpoint getUserEndpoint = findEndpoint(endpoints, "GET", "/api/users/{id}");
        assertNotNull(getUserEndpoint, "应该找到 GET /api/users/{id}");
        assertEquals("HTTP", getUserEndpoint.getEndpointType());
        assertEquals("inbound", getUserEndpoint.getDirection());
        assertEquals(Boolean.FALSE, getUserEndpoint.getIsExternal());
        
        // 验证 POST /api/users
        CodeEndpoint createUserEndpoint = findEndpoint(endpoints, "POST", "/api/users");
        assertNotNull(createUserEndpoint, "应该找到 POST /api/users");
        assertEquals("HTTP", createUserEndpoint.getEndpointType());
        
        // 验证 PUT /api/users/{id}
        CodeEndpoint updateUserEndpoint = findEndpoint(endpoints, "PUT", "/api/users/{id}");
        assertNotNull(updateUserEndpoint, "应该找到 PUT /api/users/{id}");
        
        // 验证 DELETE /api/users/{id}
        CodeEndpoint deleteUserEndpoint = findEndpoint(endpoints, "DELETE", "/api/users/{id}");
        assertNotNull(deleteUserEndpoint, "应该找到 DELETE /api/users/{id}");
        
        // 验证 GET /api/users
        CodeEndpoint listUsersEndpoint = findEndpoint(endpoints, "GET", "/api/users");
        assertNotNull(listUsersEndpoint, "应该找到 GET /api/users");
        
        // 验证 GET /api/users/search (使用 @RequestMapping)
        CodeEndpoint searchEndpoint = findEndpoint(endpoints, "GET", "/api/users/search");
        assertNotNull(searchEndpoint, "应该找到 GET /api/users/search");
        
        // 打印所有端点
        System.out.println("\n========== Spring MVC Controller 解析结果 ==========");
        endpoints.forEach(endpoint -> 
            System.out.println(String.format("  %s %s (direction=%s, external=%s)",
                endpoint.getHttpMethod(),
                endpoint.getPath(),
                endpoint.getDirection(),
                endpoint.getIsExternal()
            ))
        );
    }
    
    /**
     * 测试 Vert.x Router
     */
    @Test
    void testVertxRouter() throws IOException {
        // 1. 读取测试源代码
        String sourceCode = readTestSource("VertxRouterExample.java");
        
        // 2. 解析为 AST
        CompilationUnit cu = parseSource(sourceCode);
        
        // 3. 解析端点
        List<CodeEndpoint> endpoints = endpointParsingService.parseEndpoints(
            cu,
            "com.example.router",
            "VertxRouterExample.java",
            "/test/VertxRouterExample.java"
        );
        
        // 4. 验证结果
        assertNotNull(endpoints);
        assertEquals(5, endpoints.size(), "应该解析出 5 个端点");
        
        // 验证 GET /api/products
        CodeEndpoint getProductsEndpoint = findEndpoint(endpoints, "GET", "/api/products");
        assertNotNull(getProductsEndpoint, "应该找到 GET /api/products");
        assertEquals("HTTP", getProductsEndpoint.getEndpointType());
        assertEquals("inbound", getProductsEndpoint.getDirection());
        
        // 验证 POST /api/products
        CodeEndpoint createEndpoint = findEndpoint(endpoints, "POST", "/api/products");
        assertNotNull(createEndpoint, "应该找到 POST /api/products");
        
        // 验证 GET /api/products/:id
        CodeEndpoint getProductEndpoint = findEndpoint(endpoints, "GET", "/api/products/:id");
        assertNotNull(getProductEndpoint, "应该找到 GET /api/products/:id");
        
        // 验证 PUT /api/products/:id
        CodeEndpoint updateEndpoint = findEndpoint(endpoints, "PUT", "/api/products/:id");
        assertNotNull(updateEndpoint, "应该找到 PUT /api/products/:id");
        
        // 验证 DELETE /api/products/:id
        CodeEndpoint deleteEndpoint = findEndpoint(endpoints, "DELETE", "/api/products/:id");
        assertNotNull(deleteEndpoint, "应该找到 DELETE /api/products/:id");
        
        // 打印所有端点
        System.out.println("\n========== Vert.x Router 解析结果 ==========");
        endpoints.forEach(endpoint -> 
            System.out.println(String.format("  %s %s (direction=%s)",
                endpoint.getHttpMethod(),
                endpoint.getPath(),
                endpoint.getDirection()
            ))
        );
    }
    
    /**
     * 测试路径组合（类级别 + 方法级别）
     */
    @Test
    void testPathCombination() throws IOException {
        String sourceCode = readTestSource("SpringMvcController.java");
        CompilationUnit cu = parseSource(sourceCode);
        
        List<CodeEndpoint> endpoints = endpointParsingService.parseEndpoints(
            cu,
            "com.example.controller",
            "SpringMvcController.java",
            "/test/SpringMvcController.java"
        );
        
        // 所有路径都应该包含类级别的 /api/users 前缀
        for (CodeEndpoint endpoint : endpoints) {
            assertTrue(endpoint.getPath().startsWith("/api/users"),
                "路径应该以 /api/users 开头: " + endpoint.getPath());
        }
    }
    
    /**
     * 测试不同的 HTTP 方法
     */
    @Test
    void testHttpMethods() throws IOException {
        String sourceCode = readTestSource("SpringMvcController.java");
        CompilationUnit cu = parseSource(sourceCode);
        
        List<CodeEndpoint> endpoints = endpointParsingService.parseEndpoints(
            cu,
            "com.example.controller",
            "SpringMvcController.java",
            "/test/SpringMvcController.java"
        );
        
        // 验证包含所有标准 HTTP 方法
        assertTrue(endpoints.stream().anyMatch(e -> "GET".equals(e.getHttpMethod())));
        assertTrue(endpoints.stream().anyMatch(e -> "POST".equals(e.getHttpMethod())));
        assertTrue(endpoints.stream().anyMatch(e -> "PUT".equals(e.getHttpMethod())));
        assertTrue(endpoints.stream().anyMatch(e -> "DELETE".equals(e.getHttpMethod())));
    }
    
    /**
     * 测试包路径过滤
     */
    @Test
    void testPackageFiltering() throws IOException {
        String sourceCode = readTestSource("SpringMvcController.java");
        CompilationUnit cu = parseSource(sourceCode);
        
        // 测试匹配的包
        List<CodeEndpoint> matchedEndpoints = endpointParsingService.parseEndpoints(
            cu,
            "com.example.controller",  // 匹配 **.controller.**
            "SpringMvcController.java",
            "/test/SpringMvcController.java"
        );
        assertFalse(matchedEndpoints.isEmpty(), "controller 包应该被匹配");
        
        // 测试不匹配的包
        List<CodeEndpoint> unmatchedEndpoints = endpointParsingService.parseEndpoints(
            cu,
            "com.example.test",  // 不匹配 **.controller.**
            "SpringMvcController.java",
            "/test/SpringMvcController.java"
        );
        assertTrue(unmatchedEndpoints.isEmpty(), "test 包应该被排除");
    }
    
    // ========== 辅助方法 ==========
    
    /**
     * 读取测试源代码文件
     */
    private String readTestSource(String fileName) throws IOException {
        Path path = Paths.get("src/test/resources/test-sources/" + fileName);
        return Files.readString(path);
    }
    
    /**
     * 解析 Java 源代码为 AST
     */
    private CompilationUnit parseSource(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);  // 简化测试，不解析绑定
        parser.setUnitName("Test.java");
        
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, "21");
        options.put(JavaCore.COMPILER_SOURCE, "21");
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21");
        parser.setCompilerOptions(options);
        
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);
        
        // 调试输出
        System.out.println("AST 节点数: " + cu.types().size());
        if (!cu.types().isEmpty()) {
            System.out.println("第一个类型: " + cu.types().get(0).getClass().getSimpleName());
        }
        
        return cu;
    }
    
    /**
     * 查找指定的端点
     */
    private CodeEndpoint findEndpoint(List<CodeEndpoint> endpoints, String method, String path) {
        return endpoints.stream()
            .filter(e -> method.equals(e.getHttpMethod()) && path.equals(e.getPath()))
            .findFirst()
            .orElse(null);
    }
}

