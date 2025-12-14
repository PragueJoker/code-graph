package com.poseidon.codegraph.engine.infrastructure.config;

import com.poseidon.codegraph.engine.domain.parser.enricher.EndpointEnricher;
import com.poseidon.codegraph.engine.domain.parser.enricher.GraphEnricher;
import com.poseidon.codegraph.engine.domain.parser.endpoint.EndpointParsingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 增强器配置类
 * 负责创建和管理代码图谱增强器
 */
@Slf4j
@Configuration
public class EnricherConfig {
    
    private final EndpointParsingService endpointParsingService;
    
    public EnricherConfig(EndpointParsingService endpointParsingService) {
        this.endpointParsingService = endpointParsingService;
    }
    
    /**
     * 创建增强器列表
     * 这里集中管理所有增强器的创建和配置
     */
    @Bean
    public List<GraphEnricher> graphEnrichers() {
        List<GraphEnricher> enrichers = new ArrayList<>();
        
        // 1. 端点增强器
        EndpointEnricher endpointEnricher = new EndpointEnricher(endpointParsingService);
        enrichers.add(endpointEnricher);
        log.info("注册增强器: {}, 优先级: {}", endpointEnricher.getName(), endpointEnricher.getPriority());
        
        // 未来可以在这里添加更多增强器：
        // 2. SecurityEnricher securityEnricher = new SecurityEnricher();
        //    enrichers.add(securityEnricher);
        //
        // 3. MetricsEnricher metricsEnricher = new MetricsEnricher();
        //    enrichers.add(metricsEnricher);
        
        log.info("增强器配置完成，共注册 {} 个增强器", enrichers.size());
        return enrichers;
    }
}

