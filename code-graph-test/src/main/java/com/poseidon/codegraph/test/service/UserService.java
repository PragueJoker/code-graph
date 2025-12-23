package com.poseidon.codegraph.test.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

/**
 * 用户服务
 * 演示带有路径参数的 API 调用
 */
@Service
public class UserService {
    
    private final RestTemplate restTemplate;
    private final String baseUrl;
    
    @Value("${user.service.extra-path}")
    private String extraPath;
    
    public UserService(
            RestTemplate restTemplate,
            @Value("${user.service.url:http://localhost:8081}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }
    
    /**
     * 获取用户信息（带路径参数）
     */
    public String getUserById(Long userId) {
        String url = baseUrl + extraPath + "/api/users/" + userId;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return response.getBody();
    }
    
    /**
     * 获取用户订单（多个路径参数）
     */
    public String getUserOrders(Long userId, Long orderId) {
        String url = baseUrl + "/api/users/" + userId + "/orders/" + orderId;
        ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
        return response.getBody();
    }
    
    /**
     * 更新用户（路径参数 + 方法调用）
     */
    public void updateUser(Long userId) {
        String url = "/api/users/" + getUserIdString(userId);
        restTemplate.put(baseUrl + url, null);
    }
    
    private String getUserIdString(Long id) {
        return String.valueOf(id);
    }
}

