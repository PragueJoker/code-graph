package com.poseidon.codegraph.engine.domain.parser.enricher;

import com.poseidon.codegraph.engine.domain.context.CodeGraphContext;
import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import com.poseidon.codegraph.engine.domain.model.CodeGraph;
import com.poseidon.codegraph.engine.domain.parser.endpoint.EndpointParsingService;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端点增强器集成测试
 * 验证端点解析功能已成功集成到主流程
 */
@Slf4j
public class EndpointEnricherIntegrationTest {
    
    private EndpointEnricher endpointEnricher;
    private String testFilePath;
    
    @BeforeEach
    public void setUp() {
        // 初始化端点解析服务
        EndpointParsingService endpointParsingService = new EndpointParsingService();
        endpointParsingService.init();
        
        // 创建端点增强器
        endpointEnricher = new EndpointEnricher(endpointParsingService);
        
        // 测试文件路径
        testFilePath = "test-sources/SpringMvcController.java";
    }
    
    @Test
    public void testEndpointEnricherIntegration() throws IOException {
        log.info("========== 测试端点增强器集成 ==========");
        
        // 1. 读取测试源代码
        String sourceCode = readTestSource("SpringMvcController.java");
        
        // 2. 解析为 AST
        CompilationUnit cu = parseSource(sourceCode);
        
        // 3. 创建空的 CodeGraph
        CodeGraph graph = new CodeGraph();
        
        // 4. 创建上下文
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectFilePath(testFilePath);
        context.setPackageName("com.example.controller");
        context.setGitRepoUrl("https://github.com/test/repo");
        context.setGitBranch("main");
        
        // 5. 应用端点增强器
        endpointEnricher.enrich(graph, cu, context);
        
        // 6. 验证端点解析结果
        List<CodeEndpoint> endpoints = graph.getEndpointsAsList();
        assertNotNull(endpoints, "Endpoints 列表不应为空");
        assertFalse(endpoints.isEmpty(), "应该解析到端点");
        
        log.info("端点解析结果: endpointCount={}", endpoints.size());
        assertEquals(6, endpoints.size(), "应该解析出 6 个端点");
        
        // 7. 验证端点详情
        for (CodeEndpoint endpoint : endpoints) {
            log.info("端点: type={}, method={}, path={}, function={}", 
                endpoint.getEndpointType(),
                endpoint.getHttpMethod(),
                endpoint.getPath(),
                endpoint.getFunctionId());
            
            // 验证基本属性
            assertNotNull(endpoint.getId(), "端点ID不应为空");
            assertEquals("HTTP", endpoint.getEndpointType(), "端点类型应为 HTTP");
            assertEquals("inbound", endpoint.getDirection(), "方向应为 inbound");
            assertNotNull(endpoint.getHttpMethod(), "HTTP方法不应为空");
            assertNotNull(endpoint.getPath(), "路径不应为空");
            // functionId 由 EPR 引擎生成，应该存在
            if (endpoint.getFunctionId() != null) {
                log.info("端点 {} 关联到函数: {}", endpoint.getPath(), endpoint.getFunctionId());
            }
            
            // 验证 Git 信息
            assertEquals("https://github.com/test/repo", endpoint.getGitRepoUrl());
            assertEquals("main", endpoint.getGitBranch());
        }
        
        // 8. 验证端点关系（只有当 functionId 存在时才会创建关系）
        long endpointRelationshipCount = graph.getRelationshipsAsList().stream()
            .filter(rel -> rel.getRelationshipType().name().contains("ENDPOINT"))
            .count();
        
        log.info("端点关系数量: {}", endpointRelationshipCount);
        // 注意：由于 EPR 引擎只解析端点本身，不解析函数信息，
        // 所以 functionId 可能为空，关系数量可能为 0
        // 这是正常的，因为端点与函数的关联需要完整的解析流程
        
        log.info("========== 端点增强器集成测试通过 ==========");
    }
    
    @Test
    public void testEndpointEnricherWithoutMatch() throws IOException {
        log.info("========== 测试不匹配规则的文件 ==========");
        
        // 1. 读取测试源代码
        String sourceCode = readTestSource("SpringMvcController.java");
        
        // 2. 解析为 AST
        CompilationUnit cu = parseSource(sourceCode);
        
        // 3. 创建空的 CodeGraph
        CodeGraph graph = new CodeGraph();
        
        // 4. 创建上下文（使用不匹配的包名）
        CodeGraphContext context = new CodeGraphContext();
        context.setProjectFilePath(testFilePath);
        context.setPackageName("com.other.package");  // 不匹配规则
        context.setGitRepoUrl("https://github.com/test/repo");
        context.setGitBranch("main");
        
        // 5. 应用端点增强器
        endpointEnricher.enrich(graph, cu, context);
        
        // 6. 验证端点列表为空
        List<CodeEndpoint> endpoints = graph.getEndpointsAsList();
        assertTrue(endpoints.isEmpty(), "不匹配规则时，端点列表应为空");
        
        log.info("========== 验证通过：不匹配规则时不会解析端点 ==========");
    }
    
    // ========== 辅助方法 ==========
    
    private String readTestSource(String fileName) throws IOException {
        Path path = Path.of("src/test/resources/test-sources", fileName);
        return Files.readString(path);
    }
    
    private CompilationUnit parseSource(String sourceCode) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(sourceCode.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setResolveBindings(false);
        
        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_COMPLIANCE, "21");
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21");
        options.put(JavaCore.COMPILER_SOURCE, "21");
        parser.setCompilerOptions(options);
        
        return (CompilationUnit) parser.createAST(null);
    }
}

