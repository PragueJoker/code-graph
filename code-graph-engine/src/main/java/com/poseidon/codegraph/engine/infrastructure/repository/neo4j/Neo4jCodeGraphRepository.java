package com.poseidon.codegraph.engine.infrastructure.repository.neo4j;

import com.poseidon.codegraph.engine.application.model.*;
import com.poseidon.codegraph.engine.application.repository.CodeGraphRepository;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Values;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Neo4j 代码图谱仓储实现（基础设施层）
 */
@Slf4j
@Repository
public class Neo4jCodeGraphRepository implements CodeGraphRepository {
    
    private final Driver neo4jDriver;
    
    @Autowired
    public Neo4jCodeGraphRepository(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }
    
    // ========== 查询操作 ==========
    
    @Override
    public List<String> findWhoCallsMe(String targetFilePath) {
        log.debug("查询依赖文件: targetFile={}", targetFilePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[:CALLS]->(callee:CodeFunction)
            WHERE callee.filePath = $targetFilePath
            RETURN DISTINCT caller.filePath AS filePath
            """;
        
        try (Session session = neo4jDriver.session()) {
            List<String> result = session.run(cypher, Values.parameters("targetFilePath", targetFilePath))
                .stream()
                .map(record -> record.get("filePath").asString())
                .distinct()
                .collect(Collectors.toList());
            log.debug("查询依赖文件完成: targetFile={}, dependentCount={}", targetFilePath, result.size());
            return result;
        } catch (Exception e) {
            log.error("查询依赖文件失败: targetFile={}, error={}", targetFilePath, e.getMessage(), e);
            throw new RuntimeException("查询依赖文件失败: " + targetFilePath, e);
        }
    }
    
    @Override
    public List<CodeUnitDO> findUnitsByFilePath(String filePath) {
        String cypher = """
            MATCH (unit:CodeUnit)
            WHERE unit.filePath = $filePath
            RETURN unit
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("filePath", filePath))
                .stream()
                .map(record -> mapToCodeUnitDO(record.get("unit").asMap()))
                .collect(Collectors.toList());
        }
    }
    
    @Override
    public List<CodeFunctionDO> findFunctionsByFilePath(String filePath) {
        String cypher = """
            MATCH (func:CodeFunction)
            WHERE func.filePath = $filePath
            RETURN func
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("filePath", filePath))
                .stream()
                .map(record -> mapToCodeFunctionDO(record.get("func").asMap()))
                .collect(Collectors.toList());
        }
    }
    
    @Override
    public Optional<CodeFunctionDO> findFunctionByQualifiedName(String qualifiedName) {
        String cypher = """
            MATCH (func:CodeFunction)
            WHERE func.id = $qualifiedName OR func.qualifiedName = $qualifiedName
            RETURN func
            LIMIT 1
            """;
        
        try (Session session = neo4jDriver.session()) {
            var result = session.run(cypher, Values.parameters("qualifiedName", qualifiedName));
            
            if (result.hasNext()) {
                return Optional.of(mapToCodeFunctionDO(result.next().get("func").asMap()));
            }
            return Optional.empty();
        }
    }
    
    // ========== 删除操作 ==========
    
    @Override
    public void deleteFileOutgoingCalls(String filePath) {
        log.debug("删除文件出边: file={}", filePath);
        String cypher = """
            MATCH (caller:CodeFunction)-[r:CALLS]->()
            WHERE caller.filePath = $filePath
            DELETE r
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("filePath", filePath));
            log.info("删除文件出边成功: file={}", filePath);
        } catch (Exception e) {
            log.error("删除文件出边失败: file={}, error={}", filePath, e.getMessage(), e);
            throw new RuntimeException("删除文件出边失败: " + filePath, e);
        }
    }
    
    @Override
    public void deleteNode(String nodeId) {
        String cypher = """
            MATCH (n)
            WHERE n.id = $nodeId
            DETACH DELETE n
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("nodeId", nodeId));
            log.debug("删除节点: {}", nodeId);
        }
    }
    
    // ========== 保存操作 ==========
    
    @Override
    public void savePackage(CodePackageDO pkg) {
        String cypher = """
            MERGE (p:CodePackage {id: $id})
            SET p.name = $name,
                p.qualifiedName = $qualifiedName,
                p.language = $language,
                p.filePath = $filePath,
                p.packagePath = $packagePath
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters(
                "id", pkg.getId(),
                "name", pkg.getName(),
                "qualifiedName", pkg.getQualifiedName(),
                "language", pkg.getLanguage(),
                "filePath", pkg.getFilePath(),
                "packagePath", pkg.getPackagePath()
            ));
        }
    }
    
    @Override
    public void saveUnit(CodeUnitDO unit) {
        String cypher = """
            MERGE (u:CodeUnit {id: $id})
            SET u.name = $name,
                u.qualifiedName = $qualifiedName,
                u.language = $language,
                u.filePath = $filePath,
                u.startLine = $startLine,
                u.endLine = $endLine,
                u.unitType = $unitType,
                u.modifiers = $modifiers,
                u.isAbstract = $isAbstract,
                u.packageId = $packageId
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters(
                "id", unit.getId(),
                "name", unit.getName(),
                "qualifiedName", unit.getQualifiedName(),
                "language", unit.getLanguage(),
                "filePath", unit.getFilePath(),
                "startLine", unit.getStartLine(),
                "endLine", unit.getEndLine(),
                "unitType", unit.getUnitType(),
                "modifiers", unit.getModifiers() != null ? unit.getModifiers() : new ArrayList<>(),
                "isAbstract", unit.getIsAbstract(),
                "packageId", unit.getPackageId()
            ));
        }
    }
    
    @Override
    public void saveFunction(CodeFunctionDO function) {
        // MERGE 确保节点存在，SET 更新所有属性
        // 如果节点之前是占位符节点（isPlaceholder = true），现在会被真实数据覆盖，并移除占位符标志
        String cypher = """
            MERGE (f:CodeFunction {id: $id})
            SET f.name = $name,
                f.qualifiedName = $qualifiedName,
                f.language = $language,
                f.filePath = $filePath,
                f.startLine = $startLine,
                f.endLine = $endLine,
                f.signature = $signature,
                f.returnType = $returnType,
                f.modifiers = $modifiers,
                f.isStatic = $isStatic,
                f.isAsync = $isAsync,
                f.isConstructor = $isConstructor,
                f.isPlaceholder = $isPlaceholder
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters(
                "id", function.getId(),
                "name", function.getName(),
                "qualifiedName", function.getQualifiedName(),
                "language", function.getLanguage(),
                "filePath", function.getFilePath(),
                "startLine", function.getStartLine(),
                "endLine", function.getEndLine(),
                "signature", function.getSignature(),
                "returnType", function.getReturnType(),
                "modifiers", function.getModifiers() != null ? function.getModifiers() : new ArrayList<>(),
                "isStatic", function.getIsStatic(),
                "isAsync", function.getIsAsync(),
                "isConstructor", function.getIsConstructor(),
                "isPlaceholder", function.getIsPlaceholder() != null ? function.getIsPlaceholder() : false
            ));
        }
    }
    
    @Override
    public void saveCallRelationship(CallRelationshipDO relationship) {
        // 基础设施层只负责保存关系，占位符节点的创建由领域层处理
        // 使用 MATCH 确保节点存在（领域层已确保节点存在）
        String cypher = """
            MATCH (from:CodeFunction {id: $fromFunctionId})
            MATCH (to:CodeFunction {id: $toFunctionId})
            MERGE (from)-[r:CALLS]->(to)
            SET r.id = $id,
                r.lineNumber = $lineNumber,
                r.callType = $callType,
                r.language = $language
            """;
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters(
                "id", relationship.getId(),
                "fromFunctionId", relationship.getFromFunctionId(),
                "toFunctionId", relationship.getToFunctionId(),
                "lineNumber", relationship.getLineNumber(),
                "callType", relationship.getCallType(),
                "language", relationship.getLanguage()
            ));
        }
    }
    
    // ========== 辅助方法：映射 Neo4j 结果到 DO ==========
    
    private CodeUnitDO mapToCodeUnitDO(java.util.Map<String, Object> map) {
        CodeUnitDO unit = new CodeUnitDO();
        unit.setId((String) map.get("id"));
        unit.setName((String) map.get("name"));
        unit.setQualifiedName((String) map.get("qualifiedName"));
        unit.setLanguage((String) map.get("language"));
        unit.setFilePath((String) map.get("filePath"));
        unit.setStartLine(map.get("startLine") != null ? ((Number) map.get("startLine")).intValue() : null);
        unit.setEndLine(map.get("endLine") != null ? ((Number) map.get("endLine")).intValue() : null);
        unit.setUnitType((String) map.get("unitType"));
        unit.setModifiers(map.get("modifiers") != null ? (List<String>) map.get("modifiers") : new ArrayList<>());
        unit.setIsAbstract(map.get("isAbstract") != null ? (Boolean) map.get("isAbstract") : false);
        unit.setPackageId((String) map.get("packageId"));
        return unit;
    }
    
    private CodeFunctionDO mapToCodeFunctionDO(java.util.Map<String, Object> map) {
        CodeFunctionDO function = new CodeFunctionDO();
        function.setId((String) map.get("id"));
        function.setName((String) map.get("name"));
        function.setQualifiedName((String) map.get("qualifiedName"));
        function.setLanguage((String) map.get("language"));
        function.setFilePath((String) map.get("filePath"));
        function.setStartLine(map.get("startLine") != null ? ((Number) map.get("startLine")).intValue() : null);
        function.setEndLine(map.get("endLine") != null ? ((Number) map.get("endLine")).intValue() : null);
        function.setSignature((String) map.get("signature"));
        function.setReturnType((String) map.get("returnType"));
        function.setModifiers(map.get("modifiers") != null ? (List<String>) map.get("modifiers") : new ArrayList<>());
        function.setIsStatic(map.get("isStatic") != null ? (Boolean) map.get("isStatic") : false);
        function.setIsAsync(map.get("isAsync") != null ? (Boolean) map.get("isAsync") : false);
        function.setIsConstructor(map.get("isConstructor") != null ? (Boolean) map.get("isConstructor") : false);
        function.setIsPlaceholder(map.get("isPlaceholder") != null ? (Boolean) map.get("isPlaceholder") : false);
        return function;
    }
    
    // ========== 批量查询操作 ==========
    
    @Override
    public java.util.Set<String> findExistingFunctionsByQualifiedNames(java.util.List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        String cypher = """
            MATCH (f:CodeFunction)
            WHERE f.id IN $qualifiedNames OR f.qualifiedName IN $qualifiedNames
            RETURN DISTINCT COALESCE(f.id, f.qualifiedName) AS id
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("qualifiedNames", qualifiedNames))
                .stream()
                .map(record -> record.get("id").asString())
                .collect(java.util.stream.Collectors.toSet());
        }
    }
    
    @Override
    public java.util.Set<String> findExistingUnitsByQualifiedNames(java.util.List<String> qualifiedNames) {
        if (qualifiedNames == null || qualifiedNames.isEmpty()) {
            return new java.util.HashSet<>();
        }
        
        String cypher = """
            MATCH (u:CodeUnit)
            WHERE u.id IN $qualifiedNames OR u.qualifiedName IN $qualifiedNames
            RETURN DISTINCT COALESCE(u.id, u.qualifiedName) AS id
            """;
        
        try (Session session = neo4jDriver.session()) {
            return session.run(cypher, Values.parameters("qualifiedNames", qualifiedNames))
                .stream()
                .map(record -> record.get("id").asString())
                .collect(java.util.stream.Collectors.toSet());
        }
    }
    
    // ========== 批量保存操作 ==========
    
    @Override
    public void saveFunctionsBatch(java.util.List<CodeFunctionDO> functions) {
        if (functions == null || functions.isEmpty()) {
            log.debug("批量保存函数: 列表为空，跳过");
            return;
        }
        
        log.debug("批量保存函数开始: count={}", functions.size());
        
        try {
            // 先查询哪些节点已存在
            java.util.List<String> qualifiedNames = functions.stream()
                .map(CodeFunctionDO::getId)
                .collect(java.util.stream.Collectors.toList());
            java.util.Set<String> existingIds = findExistingFunctionsByQualifiedNames(qualifiedNames);
            
            log.debug("已存在节点: {}, 新增节点: {}", existingIds.size(), functions.size() - existingIds.size());
            
            // 分离需要插入和更新的节点
            java.util.List<CodeFunctionDO> toInsert = new java.util.ArrayList<>();
            java.util.List<CodeFunctionDO> toUpdate = new java.util.ArrayList<>();
            
            for (CodeFunctionDO func : functions) {
                if (existingIds.contains(func.getId())) {
                    toUpdate.add(func);
                } else {
                    toInsert.add(func);
                }
            }
            
            // 批量插入
            if (!toInsert.isEmpty()) {
                String insertCypher = """
                    UNWIND $functions AS func
                    CREATE (f:CodeFunction {
                        id: func.id,
                        name: func.name,
                        qualifiedName: func.qualifiedName,
                        language: func.language,
                        filePath: func.filePath,
                        startLine: func.startLine,
                        endLine: func.endLine,
                        signature: func.signature,
                        returnType: func.returnType,
                        modifiers: func.modifiers,
                        isStatic: func.isStatic,
                        isAsync: func.isAsync,
                        isConstructor: func.isConstructor,
                        isPlaceholder: func.isPlaceholder
                    })
                    """;
                
                java.util.List<java.util.Map<String, Object>> insertParams = toInsert.stream()
                    .map(this::functionToMap)
                    .collect(java.util.stream.Collectors.toList());
                
                try (Session session = neo4jDriver.session()) {
                    session.run(insertCypher, Values.parameters("functions", insertParams));
                    log.info("批量插入函数成功: count={}", toInsert.size());
                } catch (Exception e) {
                    log.error("批量插入函数失败: count={}, error={}", toInsert.size(), e.getMessage(), e);
                    throw new RuntimeException("批量插入函数失败", e);
                }
            }
        
            // 批量更新
            if (!toUpdate.isEmpty()) {
                String updateCypher = """
                    UNWIND $functions AS func
                    MATCH (f:CodeFunction {id: func.id})
                    SET f.name = func.name,
                        f.qualifiedName = func.qualifiedName,
                        f.language = func.language,
                        f.filePath = func.filePath,
                        f.startLine = func.startLine,
                        f.endLine = func.endLine,
                        f.signature = func.signature,
                        f.returnType = func.returnType,
                        f.modifiers = func.modifiers,
                        f.isStatic = func.isStatic,
                        f.isAsync = func.isAsync,
                        f.isConstructor = func.isConstructor,
                        f.isPlaceholder = func.isPlaceholder
                    """;
                
                java.util.List<java.util.Map<String, Object>> updateParams = toUpdate.stream()
                    .map(this::functionToMap)
                    .collect(java.util.stream.Collectors.toList());
                
                try (Session session = neo4jDriver.session()) {
                    session.run(updateCypher, Values.parameters("functions", updateParams));
                    log.info("批量更新函数成功: count={}", toUpdate.size());
                } catch (Exception e) {
                    log.error("批量更新函数失败: count={}, error={}", toUpdate.size(), e.getMessage(), e);
                    throw new RuntimeException("批量更新函数失败", e);
                }
            }
            
            log.info("批量保存函数完成: total={}, inserted={}, updated={}", 
                     functions.size(), toInsert.size(), toUpdate.size());
        } catch (Exception e) {
            log.error("批量保存函数失败: count={}, error={}", functions.size(), e.getMessage(), e);
            throw new RuntimeException("批量保存函数失败", e);
        }
    }
    
    @Override
    public void saveUnitsBatch(java.util.List<CodeUnitDO> units) {
        if (units == null || units.isEmpty()) {
            return;
        }
        
        // 先查询哪些节点已存在
        java.util.List<String> qualifiedNames = units.stream()
            .map(CodeUnitDO::getId)
            .collect(java.util.stream.Collectors.toList());
        java.util.Set<String> existingIds = findExistingUnitsByQualifiedNames(qualifiedNames);
        
        // 分离需要插入和更新的节点
        java.util.List<CodeUnitDO> toInsert = new java.util.ArrayList<>();
        java.util.List<CodeUnitDO> toUpdate = new java.util.ArrayList<>();
        
        for (CodeUnitDO unit : units) {
            if (existingIds.contains(unit.getId())) {
                toUpdate.add(unit);
            } else {
                toInsert.add(unit);
            }
        }
        
        // 批量插入
        if (!toInsert.isEmpty()) {
            String insertCypher = """
                UNWIND $units AS unit
                CREATE (u:CodeUnit {
                    id: unit.id,
                    name: unit.name,
                    qualifiedName: unit.qualifiedName,
                    language: unit.language,
                    filePath: unit.filePath,
                    startLine: unit.startLine,
                    endLine: unit.endLine,
                    unitType: unit.unitType,
                    modifiers: unit.modifiers,
                    isAbstract: unit.isAbstract,
                    packageId: unit.packageId
                })
                """;
            
            java.util.List<java.util.Map<String, Object>> insertParams = toInsert.stream()
                .map(this::unitToMap)
                .collect(java.util.stream.Collectors.toList());
            
            try (Session session = neo4jDriver.session()) {
                session.run(insertCypher, Values.parameters("units", insertParams));
                log.debug("批量插入单元: {} 个", toInsert.size());
            }
        }
        
        // 批量更新
        if (!toUpdate.isEmpty()) {
            String updateCypher = """
                UNWIND $units AS unit
                MATCH (u:CodeUnit {id: unit.id})
                SET u.name = unit.name,
                    u.qualifiedName = unit.qualifiedName,
                    u.language = unit.language,
                    u.filePath = unit.filePath,
                    u.startLine = unit.startLine,
                    u.endLine = unit.endLine,
                    u.unitType = unit.unitType,
                    u.modifiers = unit.modifiers,
                    u.isAbstract = unit.isAbstract,
                    u.packageId = unit.packageId
                """;
            
            java.util.List<java.util.Map<String, Object>> updateParams = toUpdate.stream()
                .map(this::unitToMap)
                .collect(java.util.stream.Collectors.toList());
            
            try (Session session = neo4jDriver.session()) {
                session.run(updateCypher, Values.parameters("units", updateParams));
                log.debug("批量更新单元: {} 个", toUpdate.size());
            }
        }
    }
    
    @Override
    public void savePackagesBatch(java.util.List<CodePackageDO> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        
        // 包通常数量很少，直接使用 MERGE
        String cypher = """
            UNWIND $packages AS pkg
            MERGE (p:CodePackage {id: pkg.id})
            SET p.name = pkg.name,
                p.qualifiedName = pkg.qualifiedName,
                p.language = pkg.language,
                p.filePath = pkg.filePath,
                p.packagePath = pkg.packagePath
            """;
        
        java.util.List<java.util.Map<String, Object>> params = packages.stream()
            .map(this::packageToMap)
            .collect(java.util.stream.Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("packages", params));
            log.debug("批量保存包: {} 个", packages.size());
        }
    }
    
    @Override
    public void saveCallRelationshipsBatch(java.util.List<CallRelationshipDO> relationships) {
        if (relationships == null || relationships.isEmpty()) {
            log.debug("批量保存调用关系: 列表为空，跳过");
            return;
        }
        
        log.debug("批量保存调用关系开始: count={}", relationships.size());
        
        String cypher = """
            UNWIND $relationships AS rel
            MATCH (from:CodeFunction {id: rel.fromFunctionId})
            MATCH (to:CodeFunction {id: rel.toFunctionId})
            MERGE (from)-[r:CALLS]->(to)
            SET r.id = rel.id,
                r.lineNumber = rel.lineNumber,
                r.callType = rel.callType,
                r.language = rel.language
            """;
        
        java.util.List<java.util.Map<String, Object>> params = relationships.stream()
            .map(this::relationshipToMap)
            .collect(java.util.stream.Collectors.toList());
        
        try (Session session = neo4jDriver.session()) {
            session.run(cypher, Values.parameters("relationships", params));
            log.info("批量保存调用关系成功: count={}", relationships.size());
        } catch (Exception e) {
            log.error("批量保存调用关系失败: count={}, error={}, " +
                      "可能原因: from/to 节点不存在（占位符节点未创建）", 
                      relationships.size(), e.getMessage(), e);
            throw new RuntimeException("批量保存调用关系失败", e);
        }
    }
    
    // ========== 辅助方法：转换为 Map ==========
    
    private java.util.Map<String, Object> functionToMap(CodeFunctionDO function) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", function.getId());
        map.put("name", function.getName());
        map.put("qualifiedName", function.getQualifiedName());
        map.put("language", function.getLanguage());
        map.put("filePath", function.getFilePath());
        map.put("startLine", function.getStartLine());
        map.put("endLine", function.getEndLine());
        map.put("signature", function.getSignature());
        map.put("returnType", function.getReturnType());
        map.put("modifiers", function.getModifiers() != null ? function.getModifiers() : new ArrayList<>());
        map.put("isStatic", function.getIsStatic());
        map.put("isAsync", function.getIsAsync());
        map.put("isConstructor", function.getIsConstructor());
        map.put("isPlaceholder", function.getIsPlaceholder() != null ? function.getIsPlaceholder() : false);
        return map;
    }
    
    private java.util.Map<String, Object> unitToMap(CodeUnitDO unit) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", unit.getId());
        map.put("name", unit.getName());
        map.put("qualifiedName", unit.getQualifiedName());
        map.put("language", unit.getLanguage());
        map.put("filePath", unit.getFilePath());
        map.put("startLine", unit.getStartLine());
        map.put("endLine", unit.getEndLine());
        map.put("unitType", unit.getUnitType());
        map.put("modifiers", unit.getModifiers() != null ? unit.getModifiers() : new ArrayList<>());
        map.put("isAbstract", unit.getIsAbstract());
        map.put("packageId", unit.getPackageId());
        return map;
    }
    
    private java.util.Map<String, Object> packageToMap(CodePackageDO pkg) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", pkg.getId());
        map.put("name", pkg.getName());
        map.put("qualifiedName", pkg.getQualifiedName());
        map.put("language", pkg.getLanguage());
        map.put("filePath", pkg.getFilePath());
        map.put("packagePath", pkg.getPackagePath());
        return map;
    }
    
    private java.util.Map<String, Object> relationshipToMap(CallRelationshipDO relationship) {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", relationship.getId());
        map.put("fromFunctionId", relationship.getFromFunctionId());
        map.put("toFunctionId", relationship.getToFunctionId());
        map.put("lineNumber", relationship.getLineNumber());
        map.put("callType", relationship.getCallType());
        map.put("language", relationship.getLanguage());
        return map;
    }
}

