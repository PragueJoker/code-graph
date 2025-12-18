package com.poseidon.codegraph.test.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Code Graph 客户端服务
 * 调用 code-graph-engine 的 REST API
 */
@Slf4j
@Service
public class CodeGraphClientService {

    private final RestTemplate restTemplate;
    private final String codeGraphEngineUrl;

    @Autowired
    public CodeGraphClientService(
            RestTemplate restTemplate,
            @Value("${codegraph.engine.url:http://localhost:8081}") String codeGraphEngineUrl) {
        this.restTemplate = restTemplate;
        this.codeGraphEngineUrl = codeGraphEngineUrl;
    }

    /**
     * 更新文件节点
     * 调用 code-graph-engine 的 PUT /api/code-graph/files/nodes
     */
    public String updateFileNodes(String projectName, String absoluteFilePath, 
                                   String projectFilePath, List<String> classpathEntries) {
        log.info("调用 code-graph-engine API: 更新文件节点");
        
        String url = codeGraphEngineUrl + "/api/code-graph/files/nodes";
        
        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectName", projectName);
        requestBody.put("absoluteFilePath", absoluteFilePath);
        requestBody.put("projectFilePath", projectFilePath);
        requestBody.put("classpathEntries", classpathEntries);
        requestBody.put("gitRepoUrl", "https://github.com/example/test-repo.git");
        requestBody.put("gitBranch", "main");
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    request,
                    String.class
            );
            
            log.info("API 调用成功: status={}, body={}", response.getStatusCode(), response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("API 调用失败: url={}, error={}", url, e.getMessage(), e);
            throw new RuntimeException("调用 code-graph-engine API 失败", e);
        }
    }

    /**
     * 删除文件节点
     * 调用 code-graph-engine 的 DELETE /api/code-graph/files/nodes
     */
    public String deleteFileNodes(String projectName, String projectFilePath) {
        log.info("调用 code-graph-engine API: 删除文件节点");
        
        String url = codeGraphEngineUrl + "/api/code-graph/files/nodes";
        
        // 构造请求体
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("projectName", projectName);
        requestBody.put("projectFilePath", projectFilePath);
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    request,
                    String.class
            );
            
            log.info("API 调用成功: status={}, body={}", response.getStatusCode(), response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("API 调用失败: url={}, error={}", url, e.getMessage(), e);
            throw new RuntimeException("调用 code-graph-engine API 失败", e);
        }
    }

    /**
     * 健康检查
     * 调用 code-graph-engine 的 GET /api/code-graph/health
     */
    public String healthCheck() {
        log.info("调用 code-graph-engine API: 健康检查");
        
        String url = codeGraphEngineUrl + "/api/code-graph/health";
        
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            log.info("健康检查成功: status={}, body={}", response.getStatusCode(), response.getBody());
            return response.getBody();
        } catch (Exception e) {
            log.error("健康检查失败: url={}, error={}", url, e.getMessage(), e);
            throw new RuntimeException("健康检查失败", e);
        }
    }
}

