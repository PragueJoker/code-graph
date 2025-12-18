package com.poseidon.codegraph.test;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Code Graph Test Application
 * 用于测试调用 code-graph-engine 的 API
 */
@SpringBootApplication
public class CodeGraphTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(CodeGraphTestApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

