# 增量更新使用示例

## 概述

本文档展示如何使用增量更新功能来处理代码变更。

## 核心组件

### 1. FileChangeHandler（领域层）

负责编排处理流程，定义业务逻辑。

**职责：**
- 解析文件（使用 JDT）
- 编排处理流程（查询依赖、删除、保存、重建）
- 通过函数集合（FunctionBundle）访问数据

### 2. IncrementalUpdateService（应用层）

负责构建函数集合，提供数据访问函数。

**职责：**
- 构建 FunctionBundle
- 提供数据访问函数（查询、删除、保存）
- 调用领域层

### 3. CodeGraphRepository（基础设施层）

负责实际的数据库操作（需要实现）。

**需要实现的方法：**
- `findWhoCallsMe(filePath)` - 查找谁依赖该文件
- `findUnitsByFilePath(filePath)` - 查找文件的所有类
- `findFunctionsByFilePath(filePath)` - 查找文件的所有方法
- `findFunctionByQualifiedName(name)` - 根据全限定名查找函数
- `deleteFileOutgoingCalls(filePath)` - 删除文件的出边
- `deleteNode(nodeId)` - 删除节点
- `saveUnit(unit)` - 保存代码单元
- `saveFunction(function)` - 保存函数
- `saveCallRelationship(relationship)` - 保存调用关系

## 使用场景

### 场景 1：Git Webhook 触发文件变更

```java
@RestController
@RequestMapping("/api/webhook/git")
public class GitWebhookController {
    
    @Autowired
    private IncrementalUpdateService updateService;
    
    @PostMapping("/push")
    public WebhookResponse handlePush(@RequestBody GitPushPayload payload) {
        List<FileChangeResponse> results = new ArrayList<>();
        
        // 处理所有变更的文件
        for (String modifiedFile : payload.getModifiedFiles()) {
            if (modifiedFile.endsWith(".java")) {
                FileChangeResponse result = updateService.handleFileModified(modifiedFile);
                results.add(result);
            }
        }
        
        // 处理所有新增的文件
        for (String addedFile : payload.getAddedFiles()) {
            if (addedFile.endsWith(".java")) {
                FileChangeResponse result = updateService.handleFileAdded(addedFile);
                results.add(result);
            }
        }
        
        // 处理所有删除的文件
        for (String deletedFile : payload.getDeletedFiles()) {
            if (deletedFile.endsWith(".java")) {
                FileChangeResponse result = updateService.handleFileDeleted(deletedFile);
                results.add(result);
            }
        }
        
        return new WebhookResponse(results);
    }
}
```

### 场景 2：手动触发文件更新

```java
@RestController
@RequestMapping("/api/update")
public class UpdateController {
    
    @Autowired
    private IncrementalUpdateService updateService;
    
    @PostMapping("/file")
    public FileChangeResponse updateFile(@RequestParam String filePath) {
        // 手动触发文件更新
        return updateService.handleFileModified(filePath);
    }
}
```

### 场景 3：文件监听器触发

```java
@Component
public class FileWatcherService {
    
    @Autowired
    private IncrementalUpdateService updateService;
    
    @EventListener
    public void onFileChanged(FileSystemEvent event) {
        String filePath = event.getPath();
        
        if (filePath.endsWith(".java")) {
            FileChangeResponse result = updateService.handleFileModified(filePath);
            
            if (result.isSuccess()) {
                log.info("文件更新成功: {}, 处理了 {} 个文件", 
                    filePath, result.getProcessedFiles());
            } else {
                log.error("文件更新失败: {}, 错误: {}", 
                    filePath, result.getErrorMessage());
            }
        }
    }
}
```

## 完整示例

### 示例：处理 UserService.java 的修改

```java
// 1. 初始状态
// 图谱中有：
//   UserService.findUser()
//   OrderService.createOrder() -> UserService.findUser()
//   PaymentService.processPayment() -> UserService.findUser()

// 2. UserService.java 被修改
String filePath = "src/main/java/com/example/UserService.java";
FileChangeResponse result = updateService.handleFileModified(filePath);

// 3. 处理流程
// 步骤 1：查找谁依赖 UserService.java
//   → [OrderService.java, PaymentService.java]
//
// 步骤 2：收集需要重建的文件
//   → [UserService.java, OrderService.java, PaymentService.java]
//
// 步骤 3：删除这些文件的出边
//   DELETE (UserService.findUser)-[CALLS]->()
//   DELETE (OrderService.createOrder)-[CALLS]->()
//   DELETE (PaymentService.processPayment)-[CALLS]->()
//
// 步骤 4：删除 UserService.java 的旧节点
//   DELETE UserService (旧)
//   DELETE UserService.findUser (旧)
//
// 步骤 5：解析 UserService.java 的新内容
//   CREATE UserService (新)
//   CREATE UserService.findUser (新)
//   CREATE UserService.saveUser (新增的方法)
//
// 步骤 6：重建所有文件的调用关系
//   CREATE (OrderService.createOrder)-[CALLS]->(UserService.findUser) (新)
//   CREATE (PaymentService.processPayment)-[CALLS]->(UserService.findUser) (新)

// 4. 查看结果
System.out.println("处理结果:");
System.out.println("  成功: " + result.isSuccess());
System.out.println("  处理文件数: " + result.getProcessedFiles());  // 3
System.out.println("  新增类: " + result.getAddedUnits());  // 1
System.out.println("  新增方法: " + result.getAddedFunctions());  // 2
System.out.println("  删除节点: " + result.getDeletedNodes());  // 2
System.out.println("  更新关系: " + result.getUpdatedCallRelationships());  // 5
System.out.println("  耗时: " + result.getDurationMs() + "ms");

// 5. 查看日志
result.getLogs().forEach(log -> System.out.println("  - " + log));
// 输出：
//   - 处理修改文件: src/main/java/com/example/UserService.java
//   - 找到依赖文件: 2 个
//   - 需要重建的文件: 3 个
//   - 删除出边完成
//   - 删除旧节点: 2 个
//   - 解析新文件: 1 个类, 2 个方法
//   - 保存新节点完成
//   - 重建调用关系: 5 条
```

## 处理结果说明

### FileChangeResponse 字段

```java
@Data
public class FileChangeResponse {
    private boolean success;                    // 是否成功
    private int processedFiles;                 // 处理的文件数量（包括级联文件）
    private int addedUnits;                     // 新增的类数量
    private int addedFunctions;                 // 新增的方法数量
    private int deletedNodes;                   // 删除的节点数量
    private int updatedCallRelationships;       // 更新的调用关系数量
    private long durationMs;                    // 处理耗时（毫秒）
    private String errorMessage;                // 错误信息
    private List<String> logs;                  // 处理日志
}
```

### 典型的处理结果

**文件修改（有依赖）：**
```
processedFiles: 3-10         // 当前文件 + 依赖文件
addedUnits: 1-5              // 新增的类
addedFunctions: 5-50         // 新增的方法
deletedNodes: 1-50           // 删除的旧节点
updatedCallRelationships: 10-200  // 重建的调用关系
durationMs: 100-1000ms       // 处理时间
```

**文件新增（无依赖）：**
```
processedFiles: 1
addedUnits: 1-5
addedFunctions: 5-50
deletedNodes: 0
updatedCallRelationships: 5-50
durationMs: 50-500ms
```

**文件删除：**
```
processedFiles: 1-10         // 当前文件 + 依赖文件
addedUnits: 0
addedFunctions: 0
deletedNodes: 1-50           // 删除的节点
updatedCallRelationships: 10-100  // 重建依赖文件的调用关系
durationMs: 50-500ms
```

## 性能考虑

### 影响性能的因素

1. **文件大小**：文件越大，解析时间越长
2. **依赖数量**：依赖文件越多，需要重建的调用关系越多
3. **方法数量**：方法越多，调用关系越多

### 典型性能指标

```
单个文件（50 个方法）：
  - 解析时间：50-100ms
  - 保存节点：10-20ms
  - 重建调用关系：50-100ms
  - 总计：100-200ms

有 5 个依赖文件：
  - 查询依赖：10ms
  - 删除出边：20ms
  - 解析文件：50-100ms
  - 保存节点：10-20ms
  - 重建 6 个文件的调用关系：300-600ms
  - 总计：400-750ms
```

## 注意事项

### 1. 必须配置 JDT 项目上下文

```java
// 需要配置完整的项目环境
ASTParser parser = ASTParser.newParser(AST.JLS21);
parser.setResolveBindings(true);  // 必须启用
parser.setEnvironment(
    classpathEntries,   // 项目依赖 + JDK
    sourcepathEntries,  // 源代码目录
    encodings,
    true
);
```

### 2. 必须创建 Neo4j 索引

```cypher
// 必须创建 filePath 索引
CREATE INDEX function_filePath FOR (f:Function) ON (f.filePath)
CREATE INDEX codeunit_filePath FOR (u:CodeUnit) ON (u.filePath)

// 必须创建 id 索引（全限定名）
CREATE INDEX function_id FOR (f:Function) ON (f.id)
CREATE INDEX codeunit_id FOR (u:CodeUnit) ON (u.id)
```

### 3. 使用全限定名作为节点 ID

```java
// 生成节点 ID
CodeFunction function = new CodeFunction();
function.setId(qualifiedName);  // 使用全限定名作为 ID
function.setName(simpleName);
function.setFilePath(filePath);
```

### 4. 处理外部依赖

```java
// 使用 MERGE 自动创建占位符节点
MATCH (from:Function {id: $fromId})
MERGE (to:Function {id: $toId})
ON CREATE SET to.isPlaceholder = true, to.isExternal = true
CREATE (from)-[:CALLS {lineNumber: $line}]->(to)
```

## 下一步

1. **实现 Neo4j Repository**
   - 连接配置
   - CRUD 操作
   - 索引创建

2. **配置 JDT 项目环境**
   - 项目根目录管理
   - Maven 依赖解析
   - Classpath 配置

3. **集成测试**
   - 真实项目测试
   - 性能测试
   - 边界情况测试

4. **监控和日志**
   - 处理时间监控
   - 错误日志记录
   - 性能指标收集

