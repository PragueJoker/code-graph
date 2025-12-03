# 增量更新设计文档

## 概述

本文档描述代码图谱的增量更新机制，用于处理 Git 文件变更事件。

## 核心概念

### 1. 变更类型（ChangeType）

- **PRIMARY（直接变更）**：来自 Git 的文件变更
  - 需要：查询依赖、删除旧节点、解析新文件、保存新节点、重建关系、触发级联
  
- **CASCADE（级联变更）**：因为依赖文件变更而需要更新
  - 只需要：删除出边、重建调用关系
  - 不需要：查询依赖、删除节点、解析文件

### 2. 文件路径作为节点属性

文件不是图数据库中的节点，而是节点的属性：

```java
CodeFunction {
    id: "com.example.UserService.findUser()",  // 全限定名作为 ID
    filePath: "src/main/java/com/example/UserService.java",  // 文件路径作为属性
    startLine: 10,
    endLine: 20
}
```

**优势：**
- 查询简单：`MATCH (n {filePath: $path})`
- 删除简单：`MATCH (n {filePath: $path}) DETACH DELETE n`
- 性能好：一层查询

**必须创建索引：**
```cypher
CREATE INDEX function_filePath FOR (f:Function) ON (f.filePath)
CREATE INDEX codeunit_filePath FOR (u:CodeUnit) ON (u.filePath)
```

### 3. 全限定名作为节点 ID

使用全限定名作为节点 ID，不使用随机 UUID：

```java
// 方法的 ID
id = "com.example.UserService.findUser(String):User"

// 类的 ID
id = "com.example.UserService"
```

**优势：**
- 不需要查询 ID（直接使用全限定名）
- ID 天然稳定（删除重建后 ID 不变）
- 语义清晰（看到 ID 就知道是什么）

## 架构设计

### 分层职责

```
领域层（Domain）：
  - FileChangeHandler：编排处理流程、解析文件
  - FunctionBundle：定义需要的函数表达式
  - 不依赖任何技术实现

应用层（Application）：
  - IncrementalUpdateService：构建执行上下文、提供函数实现
  - 调用领域层、执行数据访问

基础设施层（Infrastructure）：
  - CodeGraphRepository 实现：Neo4j 操作
```

### 函数式设计

领域层通过函数表达式定义需要的能力，应用层提供具体实现：

```java
// 领域层定义
FunctionBundle {
    Function<String, List<String>> findWhoCallsMe;
    Consumer<String> deleteFileOutgoingCalls;
    // ...
}

// 应用层实现
context.setFindWhoCallsMe(filePath -> 
    repository.findWhoCallsMe(filePath)
);
```

## 处理流程

### 文件修改流程

```
1. Git 通知：UserService.java 修改了

2. 应用层：
   - 创建 CodeChangeEvent
   - 构建 FunctionBundle
   - 调用 codeChangeService.handle(event, context)

3. 领域层：
   - 查找谁依赖 UserService.java → [OrderService.java, PaymentService.java]
   - 收集需要重建的文件 → [UserService.java, OrderService.java, PaymentService.java]
   - 删除这些文件的出边
   - 删除 UserService.java 的旧节点
   - 解析 UserService.java 的新内容
   - 保存新节点
   - 重建所有文件的调用关系

4. 结果：
   - UserService.java 更新了
   - OrderService.java 和 PaymentService.java 的调用关系也更新了
```

### 级联变更流程

```
1. UserService.java 修改触发 OrderService.java 的级联变更

2. 领域层：
   - 删除 OrderService.java 的出边
   - 重建 OrderService.java 的调用关系
   - 不删除 OrderService.java 的节点（节点没变）
   - 不查询谁依赖 OrderService.java（避免无限循环）

3. 结果：
   - OrderService.java 的节点保持不变
   - OrderService.java 的调用关系更新了（指向新的 UserService 节点）
```

## 使用示例

### 1. 处理文件修改

```java
@Autowired
private IncrementalUpdateService updateService;

// 文件修改
String filePath = "src/main/java/com/example/UserService.java";
ChangeHandleResult result = updateService.handleFileModified(filePath);

// 查看结果
System.out.println("成功: " + result.isSuccess());
System.out.println("处理文件数: " + result.getProcessedFiles());
System.out.println("新增类: " + result.getAddedUnits());
System.out.println("新增方法: " + result.getAddedFunctions());
System.out.println("删除节点: " + result.getDeletedNodes());
System.out.println("更新关系: " + result.getUpdatedCallRelationships());
System.out.println("耗时: " + result.getDurationMs() + "ms");
```

### 2. 处理文件新增

```java
String filePath = "src/main/java/com/example/OrderService.java";
ChangeHandleResult result = updateService.handleFileAdded(filePath);
```

### 3. 处理文件删除

```java
String filePath = "src/main/java/com/example/OldService.java";
ChangeHandleResult result = updateService.handleFileDeleted(filePath);
```

### 4. 处理级联变更

```java
String filePath = "src/main/java/com/example/DependentService.java";
ChangeHandleResult result = updateService.handleFileChange(
    filePath, 
    ChangeType.CASCADE
);
```

## 性能考虑

### 查询性能

```cypher
// 查询文件的所有节点（需要索引）
MATCH (n {filePath: $path})
RETURN n
// 性能：O(log n) + O(m)，其中 m 是该文件的节点数量（通常 < 100）
```

### 删除性能

```cypher
// 删除文件的所有节点和关系
MATCH (n {filePath: $path})
DETACH DELETE n
// 性能：O(m + r)，其中 m 是节点数量，r 是关系数量
```

### 级联范围控制

- 直接变更：只影响直接依赖的文件（一层）
- 级联变更：不再查询依赖，避免无限循环
- 通常一个文件变更影响 < 10 个文件

## 外部依赖处理

### 占位符节点

对于第三方库的方法（如 Spring、Apache Commons），创建占位符节点：

```java
CodeFunction {
    id: "org.apache.commons.lang3.StringUtils.isNotEmpty(CharSequence):boolean",
    isPlaceholder: true,
    isExternal: true,
    library: "commons-lang3",
    version: "3.12.0"
}
```

### MERGE 自动创建

```cypher
// 创建调用关系时，使用 MERGE 自动创建目标节点
MATCH (from:Function {id: $fromId})
MERGE (to:Function {id: $toId})
ON CREATE SET to.isPlaceholder = true, to.isExternal = true
CREATE (from)-[:CALLS {lineNumber: $line}]->(to)
```

## 下一步

1. **实现 Repository**：
   - Neo4j 连接配置
   - 实现 CodeGraphRepository 接口
   - 实现 CRUD 操作

2. **配置 JDT**：
   - 项目 classpath 配置
   - Maven 依赖解析
   - 解析器优化

3. **集成 Git Webhook**：
   - 接收 Git 事件
   - 解析变更文件列表
   - 调用增量更新服务

4. **性能优化**：
   - 批量操作
   - 异步处理
   - 缓存策略

## 参考

- Neo4j 文档：https://neo4j.com/docs/
- JDT 文档：https://help.eclipse.org/latest/index.jsp
- Spring Boot 文档：https://spring.io/projects/spring-boot

