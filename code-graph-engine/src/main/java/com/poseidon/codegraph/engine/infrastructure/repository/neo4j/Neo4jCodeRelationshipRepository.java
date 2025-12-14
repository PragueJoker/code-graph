package com.poseidon.codegraph.engine.infrastructure.repository.neo4j;

import com.poseidon.codegraph.engine.application.model.CodeRelationshipDO;
import com.poseidon.codegraph.engine.application.model.FileMetaInfo;
import com.poseidon.codegraph.engine.application.repository.CodeRelationshipRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Repository
public class Neo4jCodeRelationshipRepository implements CodeRelationshipRepository {

    private final Driver neo4jDriver;

    public Neo4jCodeRelationshipRepository(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public List<String> findWhoCallsMe(String targetProjectFilePath) {
        log.debug("查询依赖文件: targetFile={}", targetProjectFilePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[:CALLS]->(callee:CodeFunction)
            WHERE callee.projectFilePath = $targetProjectFilePath
            RETURN DISTINCT caller.projectFilePath AS projectFilePath
            """;
        
        try (Session session = neo4jDriver.session()) {
            List<String> result = session.run(cypher, Values.parameters("targetProjectFilePath", targetProjectFilePath))
                .stream()
                .map(record -> record.get("projectFilePath").asString())
                .distinct()
                .collect(Collectors.toList());
            log.debug("查询依赖文件完成: targetFile={}, dependentCount={}", targetProjectFilePath, result.size());
            return result;
        } catch (Exception e) {
            log.error("查询依赖文件失败: targetFile={}, error={}", targetProjectFilePath, e.getMessage(), e);
            throw new RuntimeException("查询依赖文件失败: " + targetProjectFilePath, e);
        }
    }

    @Override
    public List<FileMetaInfo> findWhoCallsMeWithMeta(String targetProjectFilePath) {
        log.debug("查询依赖文件（带 Git 元信息）: targetFile={}", targetProjectFilePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[:CALLS]->(callee:CodeFunction)
            WHERE callee.projectFilePath = $targetProjectFilePath
            RETURN DISTINCT 
                caller.projectFilePath AS projectFilePath,
                caller.gitRepoUrl AS gitRepoUrl,
                caller.gitBranch AS gitBranch
            """;
        
        try (Session session = neo4jDriver.session()) {
            List<FileMetaInfo> result = session.run(cypher, Values.parameters("targetProjectFilePath", targetProjectFilePath))
                .stream()
                .map(this::recordToFileMetaInfo)
                .distinct()
                .collect(Collectors.toList());
            log.debug("查询依赖文件完成（带 Git 元信息）: targetFile={}, dependentCount={}", targetProjectFilePath, result.size());
            return result;
        } catch (Exception e) {
            log.error("查询依赖文件失败（带 Git 元信息）: targetFile={}, error={}", targetProjectFilePath, e.getMessage(), e);
            throw new RuntimeException("查询依赖文件失败（带 Git 元信息）: " + targetProjectFilePath, e);
        }
    }

    private FileMetaInfo recordToFileMetaInfo(Record record) {
        FileMetaInfo meta = new FileMetaInfo();
        meta.setProjectFilePath(record.get("projectFilePath").asString(null));
        meta.setGitRepoUrl(record.get("gitRepoUrl").asString(null));
        meta.setGitBranch(record.get("gitBranch").asString(null));
        return meta;
    }

    @Override
    public void deleteFileOutgoingCalls(String projectFilePath) {
        log.debug("删除文件出边: file={}", projectFilePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[r:CALLS]->()
            WHERE caller.projectFilePath = $projectFilePath
            DELETE r
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("projectFilePath", projectFilePath));
            log.info("删除文件出边成功: file={}", projectFilePath);
        } catch (Exception e) {
            log.error("删除文件出边失败: file={}, error={}", projectFilePath, e.getMessage(), e);
            throw new RuntimeException("删除文件出边失败: " + projectFilePath, e);
        }
    }

    @Override
    public void insertRelationshipsBatch(List<CodeRelationshipDO> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return;
        }
        
        log.debug("批量插入关系开始: count={}", relationships.size());
        
        // 按关系类型分组
        List<CodeRelationshipDO> calls = new ArrayList<>();
        List<CodeRelationshipDO> packageToUnit = new ArrayList<>();
        List<CodeRelationshipDO> unitToFunction = new ArrayList<>();
        List<CodeRelationshipDO> endpointToFunction = new ArrayList<>();
        List<CodeRelationshipDO> functionToEndpoint = new ArrayList<>();
        
        for (CodeRelationshipDO rel : relationships) {
            String type = rel.getRelationshipType();
            if ("CALLS".equals(type)) {
                calls.add(rel);
            } else if ("PACKAGE_TO_UNIT".equals(type)) {
                packageToUnit.add(rel);
            } else if ("UNIT_TO_FUNCTION".equals(type)) {
                unitToFunction.add(rel);
            } else if ("ENDPOINT_TO_FUNCTION".equals(type)) {
                endpointToFunction.add(rel);
            } else if ("FUNCTION_TO_ENDPOINT".equals(type)) {
                functionToEndpoint.add(rel);
            }
        }
        
        // 批量插入 CALLS 关系
        if (!calls.isEmpty()) {
            String cypher = """
                UNWIND $relationships AS rel
                MATCH (from:CodeFunction {id: rel.fromNodeId})
                MATCH (to:CodeFunction {id: rel.toNodeId})
                CREATE (from)-[r:CALLS]->(to)
                SET r.id = rel.id,
                    r.lineNumber = rel.lineNumber,
                    r.callType = rel.callType,
                    r.language = rel.language
                """;
            
            List<Map<String, Object>> params = calls.stream()
                .map(this::relationshipToMap)
                .collect(Collectors.toList());
            
            try (Session session = neo4jDriver.session()) {
                session.run(cypher, Values.parameters("relationships", params));
                log.info("批量插入 CALLS 关系成功: count={}", calls.size());
            } catch (Exception e) {
                log.error("批量插入 CALLS 关系失败: count={}, error={}", calls.size(), e.getMessage(), e);
                throw new RuntimeException("批量插入 CALLS 关系失败", e);
            }
        }
        
        // 批量插入 PACKAGE_TO_UNIT 关系
        if (!packageToUnit.isEmpty()) {
            String cypher = """
                UNWIND $relationships AS rel
                MATCH (from:CodePackage {id: rel.fromNodeId})
                MATCH (to:CodeUnit {id: rel.toNodeId})
                CREATE (from)-[r:PACKAGE_TO_UNIT]->(to)
                SET r.id = rel.id,
                    r.relationshipType = rel.relationshipType,
                    r.language = rel.language
                """;
            
            List<Map<String, Object>> params = packageToUnit.stream()
                .map(this::relationshipToMap)
                .collect(Collectors.toList());
            
            try (Session session = neo4jDriver.session()) {
                session.run(cypher, Values.parameters("relationships", params));
                log.info("批量插入 PACKAGE_TO_UNIT 关系成功: count={}", packageToUnit.size());
            } catch (Exception e) {
                log.error("批量插入 PACKAGE_TO_UNIT 关系失败: count={}, error={}", packageToUnit.size(), e.getMessage(), e);
                throw new RuntimeException("批量插入 PACKAGE_TO_UNIT 关系失败", e);
            }
        }
        
        // 批量插入 UNIT_TO_FUNCTION 关系
        if (!unitToFunction.isEmpty()) {
            String cypher = """
                UNWIND $relationships AS rel
                MATCH (from:CodeUnit {id: rel.fromNodeId})
                MATCH (to:CodeFunction {id: rel.toNodeId})
                CREATE (from)-[r:UNIT_TO_FUNCTION]->(to)
                SET r.id = rel.id,
                    r.relationshipType = rel.relationshipType,
                    r.language = rel.language
                """;
            
            List<Map<String, Object>> params = unitToFunction.stream()
                .map(this::relationshipToMap)
                .collect(Collectors.toList());
            
            try (Session session = neo4jDriver.session()) {
                session.run(cypher, Values.parameters("relationships", params));
                log.info("批量插入 UNIT_TO_FUNCTION 关系成功: count={}", unitToFunction.size());
            } catch (Exception e) {
                log.error("批量插入 UNIT_TO_FUNCTION 关系失败: count={}, error={}", unitToFunction.size(), e.getMessage(), e);
                throw new RuntimeException("批量插入 UNIT_TO_FUNCTION 关系失败", e);
            }
        }
        
        // 批量插入 ENDPOINT_TO_FUNCTION 关系
        if (!endpointToFunction.isEmpty()) {
            String cypher = """
                UNWIND $relationships AS rel
                MATCH (from:CodeEndpoint {id: rel.fromNodeId})
                MATCH (to:CodeFunction {id: rel.toNodeId})
                CREATE (from)-[r:ENDPOINT_TO_FUNCTION]->(to)
                SET r.id = rel.id,
                    r.relationshipType = rel.relationshipType,
                    r.language = rel.language
                """;
            
            List<Map<String, Object>> params = endpointToFunction.stream()
                .map(this::relationshipToMap)
                .collect(Collectors.toList());
            
            try (Session session = neo4jDriver.session()) {
                session.run(cypher, Values.parameters("relationships", params));
                log.info("批量插入 ENDPOINT_TO_FUNCTION 关系成功: count={}", endpointToFunction.size());
            } catch (Exception e) {
                log.error("批量插入 ENDPOINT_TO_FUNCTION 关系失败: count={}, error={}", endpointToFunction.size(), e.getMessage(), e);
                throw new RuntimeException("批量插入 ENDPOINT_TO_FUNCTION 关系失败", e);
            }
        }
        
        // 批量插入 FUNCTION_TO_ENDPOINT 关系
        if (!functionToEndpoint.isEmpty()) {
            String cypher = """
                UNWIND $relationships AS rel
                MATCH (from:CodeFunction {id: rel.fromNodeId})
                MATCH (to:CodeEndpoint {id: rel.toNodeId})
                CREATE (from)-[r:FUNCTION_TO_ENDPOINT]->(to)
                SET r.id = rel.id,
                    r.relationshipType = rel.relationshipType,
                    r.language = rel.language
                """;
            
            List<Map<String, Object>> params = functionToEndpoint.stream()
                .map(this::relationshipToMap)
                .collect(Collectors.toList());
            
            try (Session session = neo4jDriver.session()) {
                session.run(cypher, Values.parameters("relationships", params));
                log.info("批量插入 FUNCTION_TO_ENDPOINT 关系成功: count={}", functionToEndpoint.size());
            } catch (Exception e) {
                log.error("批量插入 FUNCTION_TO_ENDPOINT 关系失败: count={}, error={}", functionToEndpoint.size(), e.getMessage(), e);
                throw new RuntimeException("批量插入 FUNCTION_TO_ENDPOINT 关系失败", e);
            }
        }
        
        log.info("批量插入关系完成: total={}, CALLS={}, PACKAGE_TO_UNIT={}, UNIT_TO_FUNCTION={}, ENDPOINT_TO_FUNCTION={}, FUNCTION_TO_ENDPOINT={}", 
                 relationships.size(), calls.size(), packageToUnit.size(), unitToFunction.size(), 
                 endpointToFunction.size(), functionToEndpoint.size());
    }

    private Map<String, Object> relationshipToMap(CodeRelationshipDO relationship) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", relationship.getId());
        
        // 优先使用通用字段
        if (relationship.getFromNodeId() != null) {
            map.put("fromNodeId", relationship.getFromNodeId());
        } else if (relationship.getFromFunctionId() != null) {
            map.put("fromNodeId", relationship.getFromFunctionId());
        }
        
        if (relationship.getToNodeId() != null) {
            map.put("toNodeId", relationship.getToNodeId());
        } else if (relationship.getToFunctionId() != null) {
            map.put("toNodeId", relationship.getToFunctionId());
        }
        
        // 兼容性字段（用于 CALLS 关系）
        map.put("fromFunctionId", relationship.getFromFunctionId());
        map.put("toFunctionId", relationship.getToFunctionId());
        
        // 关系类型
        map.put("relationshipType", relationship.getRelationshipType());
        
        // CALLS 关系特有字段
        map.put("lineNumber", relationship.getLineNumber());
        map.put("callType", relationship.getCallType());
        map.put("language", relationship.getLanguage());
        return map;
    }

    @Override
    public java.util.Set<String> findExistingStructureRelationships(List<CodeRelationshipDO> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        // 构建查询条件：检查 (fromNodeId, toNodeId, relationshipType) 的组合是否存在
        List<Map<String, Object>> relPairs = relationships.stream()
            .map(rel -> {
                Map<String, Object> pair = new HashMap<>();
                pair.put("fromNodeId", rel.getFromNodeId());
                pair.put("toNodeId", rel.getToNodeId());
                pair.put("relationshipType", rel.getRelationshipType());
                return pair;
            })
            .collect(Collectors.toList());
        
        String cypher = """
            UNWIND $relPairs AS pair
            MATCH (from)-[r]->(to)
            WHERE from.id = pair.fromNodeId 
              AND to.id = pair.toNodeId
              AND type(r) = pair.relationshipType
            RETURN pair.fromNodeId + ':' + pair.toNodeId + ':' + pair.relationshipType AS key
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("relPairs", relPairs))
                .stream()
                .map(record -> record.get("key").asString())
                .collect(Collectors.toSet());
        }
    }
}

