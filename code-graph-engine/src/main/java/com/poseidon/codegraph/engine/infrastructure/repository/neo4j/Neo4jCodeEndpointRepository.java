package com.poseidon.codegraph.engine.infrastructure.repository.neo4j;

import com.poseidon.codegraph.engine.application.model.CodeEndpointDO;
import com.poseidon.codegraph.engine.application.repository.CodeEndpointRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Neo4j 端点仓储实现
 */
@Slf4j
@Repository
public class Neo4jCodeEndpointRepository implements CodeEndpointRepository {
    
    private final Driver driver;
    
    public Neo4jCodeEndpointRepository(Driver driver) {
        this.driver = driver;
    }
    
    @Override
    public void insertEndpointsBatch(List<CodeEndpointDO> endpoints) {
        if (endpoints.isEmpty()) {
            return;
        }
        
        String cypher = """
            UNWIND $endpoints AS endpoint
            CREATE (e:CodeEndpoint {
                id: endpoint.id,
                name: endpoint.name,
                qualifiedName: endpoint.qualifiedName,
                projectFilePath: endpoint.projectFilePath,
                gitRepoUrl: endpoint.gitRepoUrl,
                gitBranch: endpoint.gitBranch,
                language: endpoint.language,
                startLine: endpoint.startLine,
                endLine: endpoint.endLine,
                endpointType: endpoint.endpointType,
                direction: endpoint.direction,
                isExternal: endpoint.isExternal,
                httpMethod: endpoint.httpMethod,
                path: endpoint.path,
                normalizedPath: endpoint.normalizedPath,
                topic: endpoint.topic,
                operation: endpoint.operation,
                keyPattern: endpoint.keyPattern,
                dataStructure: endpoint.dataStructure,
                tableName: endpoint.tableName,
                dbOperation: endpoint.dbOperation,
                serviceName: endpoint.serviceName,
                parseLevel: endpoint.parseLevel,
                targetService: endpoint.targetService,
                functionId: endpoint.functionId
            })
            """;
        
        try (Session session = driver.session()) {
            session.run(cypher, Map.of("endpoints", toMapList(endpoints)));
            log.info("批量插入端点成功: count={}", endpoints.size());
        } catch (Exception e) {
            log.error("批量插入端点失败: error={}", e.getMessage(), e);
            throw new RuntimeException("批量插入端点失败", e);
        }
    }
    
    @Override
    public void updateEndpointsBatch(List<CodeEndpointDO> endpoints) {
        if (endpoints.isEmpty()) {
            return;
        }
        
        String cypher = """
            UNWIND $endpoints AS endpoint
            MATCH (e:CodeEndpoint {id: endpoint.id})
            SET e.name = endpoint.name,
                e.qualifiedName = endpoint.qualifiedName,
                e.projectFilePath = endpoint.projectFilePath,
                e.gitRepoUrl = endpoint.gitRepoUrl,
                e.gitBranch = endpoint.gitBranch,
                e.language = endpoint.language,
                e.startLine = endpoint.startLine,
                e.endLine = endpoint.endLine,
                e.endpointType = endpoint.endpointType,
                e.direction = endpoint.direction,
                e.isExternal = endpoint.isExternal,
                e.httpMethod = endpoint.httpMethod,
                e.path = endpoint.path,
                e.normalizedPath = endpoint.normalizedPath,
                e.topic = endpoint.topic,
                e.operation = endpoint.operation,
                e.keyPattern = endpoint.keyPattern,
                e.dataStructure = endpoint.dataStructure,
                e.tableName = endpoint.tableName,
                e.dbOperation = endpoint.dbOperation,
                e.serviceName = endpoint.serviceName,
                e.parseLevel = endpoint.parseLevel,
                e.targetService = endpoint.targetService,
                e.functionId = endpoint.functionId
            """;
        
        try (Session session = driver.session()) {
            session.run(cypher, Map.of("endpoints", toMapList(endpoints)));
            log.info("批量更新端点成功: count={}", endpoints.size());
        } catch (Exception e) {
            log.error("批量更新端点失败: error={}", e.getMessage(), e);
            throw new RuntimeException("批量更新端点失败", e);
        }
    }
    
    @Override
    public void deleteById(String id) {
        String cypher = "MATCH (e:CodeEndpoint {id: $id}) DETACH DELETE e";
        
        try (Session session = driver.session()) {
            session.run(cypher, Map.of("id", id));
            log.debug("删除端点成功: id={}", id);
        } catch (Exception e) {
            log.error("删除端点失败: id={}, error={}", id, e.getMessage(), e);
            throw new RuntimeException("删除端点失败: " + id, e);
        }
    }
    
    @Override
    public Set<String> findExistingEndpointsByIds(List<String> ids) {
        if (ids.isEmpty()) {
            return Collections.emptySet();
        }
        
        String cypher = """
            MATCH (e:CodeEndpoint)
            WHERE e.id IN $ids
            RETURN e.id AS id
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("ids", ids));
            return result.stream()
                .map(record -> record.get("id").asString())
                .collect(Collectors.toSet());
        } catch (Exception e) {
            log.error("查询端点存在性失败: error={}", e.getMessage(), e);
            throw new RuntimeException("查询端点存在性失败", e);
        }
    }
    
    @Override
    public List<CodeEndpointDO> findEndpointsByProjectFilePath(String projectFilePath) {
        String cypher = """
            MATCH (e:CodeEndpoint {projectFilePath: $projectFilePath})
            RETURN e
            """;
        
        try (Session session = driver.session()) {
            Result result = session.run(cypher, Map.of("projectFilePath", projectFilePath));
            List<CodeEndpointDO> endpoints = new ArrayList<>();
            
            result.stream().forEach(record -> {
                var node = record.get("e").asNode();
                CodeEndpointDO endpoint = new CodeEndpointDO();
                endpoint.setId(node.get("id").asString());
                endpoint.setName(node.get("name").asString(null));
                endpoint.setQualifiedName(node.get("qualifiedName").asString(null));
                endpoint.setProjectFilePath(node.get("projectFilePath").asString(null));
                endpoint.setGitRepoUrl(node.get("gitRepoUrl").asString(null));
                endpoint.setGitBranch(node.get("gitBranch").asString(null));
                endpoint.setLanguage(node.get("language").asString(null));
                endpoint.setStartLine(node.get("startLine").asInt(0));
                endpoint.setEndLine(node.get("endLine").asInt(0));
                endpoint.setEndpointType(node.get("endpointType").asString(null));
                endpoint.setDirection(node.get("direction").asString(null));
                endpoint.setIsExternal(node.get("isExternal").asBoolean(false));
                endpoint.setHttpMethod(node.get("httpMethod").asString(null));
                endpoint.setPath(node.get("path").asString(null));
                endpoint.setNormalizedPath(node.get("normalizedPath").asString(null));
                endpoint.setTopic(node.get("topic").asString(null));
                endpoint.setOperation(node.get("operation").asString(null));
                endpoint.setKeyPattern(node.get("keyPattern").asString(null));
                endpoint.setDataStructure(node.get("dataStructure").asString(null));
                endpoint.setTableName(node.get("tableName").asString(null));
                endpoint.setDbOperation(node.get("dbOperation").asString(null));
                endpoint.setServiceName(node.get("serviceName").asString(null));
                endpoint.setParseLevel(node.get("parseLevel").asString(null));
                endpoint.setTargetService(node.get("targetService").asString(null));
                endpoint.setFunctionId(node.get("functionId").asString(null));
                endpoints.add(endpoint);
            });
            
            return endpoints;
        } catch (Exception e) {
            log.error("查询端点失败: projectFilePath={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("查询端点失败: " + projectFilePath, e);
        }
    }
    
    private List<Map<String, Object>> toMapList(List<CodeEndpointDO> endpoints) {
        return endpoints.stream().map(this::toMap).collect(Collectors.toList());
    }
    
    private Map<String, Object> toMap(CodeEndpointDO endpoint) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", endpoint.getId());
        map.put("name", endpoint.getName());
        map.put("qualifiedName", endpoint.getQualifiedName());
        map.put("projectFilePath", endpoint.getProjectFilePath());
        map.put("gitRepoUrl", endpoint.getGitRepoUrl());
        map.put("gitBranch", endpoint.getGitBranch());
        map.put("language", endpoint.getLanguage());
        map.put("startLine", endpoint.getStartLine());
        map.put("endLine", endpoint.getEndLine());
        map.put("endpointType", endpoint.getEndpointType());
        map.put("direction", endpoint.getDirection());
        map.put("isExternal", endpoint.getIsExternal());
        map.put("httpMethod", endpoint.getHttpMethod());
        map.put("path", endpoint.getPath());
        map.put("normalizedPath", endpoint.getNormalizedPath());
        map.put("topic", endpoint.getTopic());
        map.put("operation", endpoint.getOperation());
        map.put("keyPattern", endpoint.getKeyPattern());
        map.put("dataStructure", endpoint.getDataStructure());
        map.put("tableName", endpoint.getTableName());
        map.put("dbOperation", endpoint.getDbOperation());
        map.put("serviceName", endpoint.getServiceName());
        map.put("parseLevel", endpoint.getParseLevel());
        map.put("targetService", endpoint.getTargetService());
        map.put("functionId", endpoint.getFunctionId());
        return map;
    }
}

