# 代码图谱增量更新设计总结

## 核心设计决策

### 1. 文件路径作为节点属性（不是节点）✅

**决策：** 文件不作为图数据库中的节点，而是作为节点的属性。

**理由：**
- 文件是物理组织形式，不是语义概念
- 查询简单：`MATCH (n {filePath: $path})`
- 删除简单：`MATCH (n {filePath: $path}) DETACH DELETE n`
- 性能好：一层查询，不需要多层遍历

**实现：**
```java
CodeFunction {
    id: "com.example.UserService.findUser()",
    filePath: "src/main/java/com/example/UserService.java",  // 属性
    startLine: 10,
    endLine: 20
}
```

**必须创建索引：**
```cypher
CREATE INDEX function_filePath FOR (f:Function) ON (f.filePath)
```

---

### 2. 全限定名作为节点 ID ✅

**决策：** 使用全限定名作为节点 ID，不使用随机 UUID。

**理由：**
- 不需要查询 ID（直接使用全限定名）
- ID 天然稳定（删除重建后 ID 不变）
- 语义清晰（看到 ID 就知道是什么）
- 调试友好

**实现：**
```java
// 方法的 ID
id = "com.example.UserService.findUser(String):User"

// 类的 ID
id = "com.example.UserService"
```

---

### 3. 区分直接变更和级联变更 ✅

**决策：** 使用 `ChangeType` 区分直接变更（PRIMARY）和级联变更（CASCADE）。

**理由：**
- 直接变更需要查询依赖、删除节点、解析文件、触发级联
- 级联变更只需要重建调用关系，避免无限循环
- 性能优化：级联变更不删除节点，节点 ID 保持稳定

**实现：**
```java
enum ChangeType {
    PRIMARY,   // 直接变更（来自 Git）
    CASCADE    // 级联变更（来自依赖）
}
```

---

### 4. 函数式设计：领域层定义协议，应用层提供实现 ✅

**决策：** 领域层通过函数表达式定义需要的能力，应用层提供具体实现。

**理由：**
- 领域层完全解耦，不依赖任何技术实现
- 易于测试（可以 Mock 函数）
- 灵活扩展（可以提供不同的实现）

**实现：**
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

---

### 5. 占位符节点处理外部依赖 ✅

**决策：** 使用 MERGE 自动创建占位符节点，处理外部依赖（JDK、第三方库）。

**理由：**
- 不需要关心解析顺序
- 可以处理外部依赖
- 符合图数据库的设计理念（关系必须连接节点）

**实现：**
```cypher
MATCH (from:Function {id: $fromId})
MERGE (to:Function {id: $toId})
ON CREATE SET to.isPlaceholder = true, to.isExternal = true
CREATE (from)-[:CALLS {lineNumber: $line}]->(to)
```

---

## 处理流程

### 直接变更（PRIMARY）

```
输入：UserService.java 修改

流程：
1. 查找谁依赖 UserService.java
   → [OrderService.java, PaymentService.java]

2. 收集需要重建的文件
   → [UserService.java, OrderService.java, PaymentService.java]

3. 删除这些文件的出边
   → 删除 3 个文件的所有 CALLS 出边

4. 删除 UserService.java 的旧节点
   → 删除旧的类和方法节点

5. 解析 UserService.java 的新内容
   → 使用 JDT 解析

6. 保存新节点
   → 创建新的类和方法节点

7. 重建所有文件的调用关系
   → 重建 3 个文件的调用关系

结果：
  - UserService.java 更新了
  - OrderService.java 和 PaymentService.java 的调用关系也更新了
  - 不会触发更多级联（避免无限循环）
```

---

### 级联变更（CASCADE）

```
输入：OrderService.java 级联变更（因为 UserService.java 变更了）

流程：
1. 不查询谁依赖 OrderService.java（避免循环）

2. 删除 OrderService.java 的出边
   → 删除所有 CALLS 出边

3. 重建 OrderService.java 的调用关系
   → 重新解析，创建新的调用关系

结果：
  - OrderService.java 的节点保持不变（ID 不变）
  - OrderService.java 的调用关系更新了
  - 不会触发更多级联
```

---

## 关键技术点

### 1. JDT 需要完整的项目上下文

**前提条件：**
- Clone 完整的项目代码
- 构建项目（下载依赖）
- 配置 classpath（项目依赖 + JDK）
- 配置 sourcepath（源代码目录）

**原因：**
- JDT 需要解析类型信息（`resolveMethodBinding()`）
- 需要知道被调用方法的完整签名
- 需要获取全限定名

---

### 2. 图数据库的约束

**规则：** 关系必须连接两个已存在的节点。

**解决方案：** 使用 MERGE 自动创建占位符节点。

**原因：**
- 图数据库的关系存储物理指针，不是 ID
- 删除节点时，必须同时删除所有相关的边（DETACH DELETE）
- 这是图数据库的设计理念，不是我们的选择

---

### 3. 避免级联扩散

**问题：** 如果级联变更也删除节点，会导致无限级联。

**解决方案：**
- 直接变更：删除节点 + 重建节点 + 重建调用关系
- 级联变更：只重建调用关系（不删除节点）

**关键：**
- 级联变更不查询依赖
- 级联变更不删除节点
- 节点 ID 保持稳定（使用全限定名）

---

## 架构分层

### 领域层（Domain）

**职责：** 定义业务逻辑，不依赖技术实现。

**组件：**
- `CodeChangeEvent` - 变更事件
- `ChangeType` - 变更类型
- `FunctionBundle` - 执行上下文（函数表达式）
- `FileChangeHandler` - 变更处理服务（编排逻辑）
- `ChangeHandleResult` - 处理结果
- `JavaCodeParser` - 解析器（使用 JDT）

---

### 应用层（Application）

**职责：** 构建执行上下文，提供数据访问函数，调用领域层。

**组件：**
- `IncrementalUpdateService` - 增量更新服务
- 构建 `FunctionBundle`
- 提供数据访问函数实现

---

### 基础设施层（Infrastructure）

**职责：** 实现数据访问，连接 Neo4j。

**组件：**
- `CodeGraphRepository` 实现
- Neo4j 连接配置
- CRUD 操作

---

### 接口层（Interfaces）

**职责：** 对外暴露 API。

**组件：**
- `GitWebhookController` - 接收 Git Webhook
- `UpdateController` - 手动触发更新

---

## 数据流

```
Git Webhook
    ↓
GitWebhookController (接口层)
    ↓
IncrementalUpdateService (应用层)
    ├─ 构建 CodeChangeEvent
    ├─ 构建 FunctionBundle
    │   ├─ findWhoCallsMe: filePath -> repository.findWhoCallsMe(filePath)
    │   ├─ deleteFileOutgoingCalls: filePath -> repository.deleteFileOutgoingCalls(filePath)
    │   └─ saveFunction: function -> repository.saveFunction(function)
    └─ 调用 FileChangeHandler.handle(event, context)
        ↓
FileChangeHandler (领域层)
    ├─ 编排处理流程
    ├─ 调用 context.findWhoCallsMe(filePath)
    │   └─ 应用层执行：repository.findWhoCallsMe(filePath)
    │       └─ 基础设施层执行：Neo4j 查询
    ├─ 调用 javaCodeParser.parseFile(filePath)
    │   └─ 领域层自己执行：JDT 解析
    └─ 调用 context.saveFunction(function)
        └─ 应用层执行：repository.saveFunction(function)
            └─ 基础设施层执行：Neo4j 保存
```

---

## 下一步实现

### 1. 实现 Neo4j Repository（优先级：高）

```java
@Repository
public class Neo4jCodeGraphRepository implements CodeGraphRepository {
    // 实现所有接口方法
}
```

### 2. 配置 JDT 项目环境（优先级：高）

```java
@Component
public class JdtProjectConfig {
    public ASTParser createParser(String projectRoot) {
        // 配置 classpath 和 sourcepath
    }
}
```

### 3. 实现 Git Webhook 接收（优先级：中）

```java
@RestController
public class GitWebhookController {
    @PostMapping("/webhook/git/push")
    public WebhookResponse handlePush(@RequestBody GitPushPayload payload) {
        // 处理 Git 事件
    }
}
```

### 4. 项目管理服务（优先级：中）

```java
@Service
public class ProjectManagementService {
    public void initProject(String repoUrl);
    public void updateProject(String projectId);
}
```

---

## 总结

本设计通过以下关键决策，实现了高效、可维护的增量更新机制：

1. ✅ 文件路径作为属性，简化查询和删除
2. ✅ 全限定名作为 ID，避免查询开销
3. ✅ 区分直接变更和级联变更，避免无限循环
4. ✅ 函数式设计，实现领域层和应用层解耦
5. ✅ 占位符节点，处理外部依赖

这个设计在性能、可维护性和扩展性之间取得了良好的平衡。

