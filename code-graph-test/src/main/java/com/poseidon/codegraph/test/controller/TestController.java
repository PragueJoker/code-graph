package com.poseidon.codegraph.test.controller;

import com.poseidon.codegraph.test.service.CodeGraphClientService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 测试 Controller
 * 提供测试接口，内部会调用 code-graph-engine 的 API
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
public class TestController {

    private final CodeGraphClientService codeGraphClientService;

    @Autowired
    public TestController(CodeGraphClientService codeGraphClientService) {
        this.codeGraphClientService = codeGraphClientService;
    }

    /**
     * 触发解析文件
     * 接收参数后，调用 code-graph-engine 的 API 进行解析
     */
    @PostMapping("/parse-file")
    public Map<String, Object> parseFile(@RequestBody Map<String, Object> request) {
        log.info("接收到解析文件请求: {}", request);
        
        String projectName = (String) request.get("projectName");
        String absoluteFilePath = (String) request.get("absoluteFilePath");
        String projectFilePath = (String) request.get("projectFilePath");
        @SuppressWarnings("unchecked")
        List<String> classpathEntries = (List<String>) request.get("classpathEntries");
        
        // 调用 code-graph-engine API
        String result = codeGraphClientService.updateFileNodes(
                projectName, 
                absoluteFilePath, 
                projectFilePath, 
                classpathEntries
        );
        
        return Map.of(
                "status", "success",
                "message", "文件解析请求已发送",
                "engineResponse", result
        );
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/files")
    public Map<String, Object> deleteFile(@RequestBody Map<String, Object> request) {
        log.info("接收到删除文件请求: {}", request);
        
        String projectName = (String) request.get("projectName");
        String projectFilePath = (String) request.get("projectFilePath");
        
        // 调用 code-graph-engine API
        String result = codeGraphClientService.deleteFileNodes(projectName, projectFilePath);
        
        return Map.of(
                "status", "success",
                "message", "文件删除请求已发送",
                "engineResponse", result
        );
    }

    /**
     * 健康检查
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        log.info("接收到健康检查请求");
        
        // 检查自身状态
        boolean selfHealthy = true;
        
        // 检查 code-graph-engine 状态
        boolean engineHealthy = false;
        String engineStatus = null;
        try {
            engineStatus = codeGraphClientService.healthCheck();
            engineHealthy = true;
        } catch (Exception e) {
            engineStatus = "UNHEALTHY: " + e.getMessage();
        }
        
        return Map.of(
                "service", "code-graph-test",
                "status", selfHealthy && engineHealthy ? "UP" : "DEGRADED",
                "self", selfHealthy ? "UP" : "DOWN",
                "codeGraphEngine", engineHealthy ? "UP" : "DOWN",
                "engineResponse", engineStatus
        );
    }
}

