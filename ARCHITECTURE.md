# 架构设计文档

## 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         接口层 (Interfaces)                      │
│  ┌──────────────────┐         ┌──────────────────┐              │
│  │ GitWebhook       │         │ REST API         │              │
│  │ Controller       │         │ Controller       │              │
│  └────────┬─────────┘         └────────┬─────────┘              │
└───────────┼──────────────────────────┼────────────────────────┘
            │                          │
            ↓                          ↓
┌─────────────────────────────────────────────────────────────────┐
│                        应用层 (Application)                      │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ IncrementalUpdateService                                 │   │
│  │  - 构建 FunctionBundle                        │   │
│  │  - 提供数据访问函数                                      │   │
│  │  - 调用领域层                                            │   │
│  └────────┬───────────────────────────────────────────────┘   │
└───────────┼──────────────────────────────────────────────────┘
            │
            ↓ 传入 context（函数表达式）
┌─────────────────────────────────────────────────────────────────┐
│                         领域层 (Domain)                          │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ FileChangeHandler                                        │   │
│  │  - 编排处理流程                                          │   │
│  │  - 解析文件（使用 JDT）                                  │   │
│  │  - 通过 context 访问数据                                 │   │
│  └────────┬───────────────────────────────────────────────┘   │
│           │                                                     │
│           ├─ 调用 context.findWhoCallsMe(filePath)             │
│           ├─ 调用 context.deleteFileOutgoingCalls(filePath)    │
│           ├─ 调用 javaCodeParser.parseFile(filePath)           │
│           └─ 调用 context.saveFunction(function)               │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ JavaCodeParser                                           │   │
│  │  - 使用 JDT 解析 Java 代码                               │   │
│  │  - 构建领域模型                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
            ↑
            │ 函数表达式执行
            ↓
┌─────────────────────────────────────────────────────────────────┐
│                      基础设施层 (Infrastructure)                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Neo4jCodeGraphRepository (待实现)                        │   │
│  │  - 实现 CodeGraphRepository 接口                         │   │
│  │  - Neo4j 查询和保存                                      │   │
│  └────────┬───────────────────────────────────────────────┘   │
└───────────┼──────────────────────────────────────────────────┘
            │
            ↓
┌─────────────────────────────────────────────────────────────────┐
│                          Neo4j 数据库                            │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 节点：                                                   │   │
│  │  - CodeUnit (类、接口、枚举)                            │   │
│  │  - Function (方法)                                       │   │
│  │                                                          │   │
│  │ 关系：                                                   │   │
│  │  - CALLS (方法调用)                                      │   │
│  │  - CONTAINS (包含关系)                                   │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 数据流

### 文件修改流程

```
1. Git Push Event
   ↓
2. GitWebhookController
   - 接收 Webhook
   - 解析变更文件列表
   ↓
3. IncrementalUpdateService
   - 创建 CodeChangeEvent
   - 构建 FunctionBundle
     context.findWhoCallsMe = filePath -> repository.findWhoCallsMe(filePath)
     context.deleteNode = nodeId -> repository.deleteNode(nodeId)
     context.saveFunction = func -> repository.saveFunction(func)
   ↓
4. FileChangeHandler.handle(event, context)
   - 步骤 1：查询依赖
     dependentFiles = context.findWhoCallsMe.apply(filePath)
     ↓ 执行
     repository.findWhoCallsMe(filePath)
     ↓ 执行
     Neo4j: MATCH (caller)-[:CALLS]->(callee {filePath: $path})
     
   - 步骤 2：删除出边
     context.deleteFileOutgoingCalls.accept(filePath)
     ↓ 执行
     repository.deleteFileOutgoingCalls(filePath)
     ↓ 执行
     Neo4j: MATCH (f {filePath: $path})-[r:CALLS]->() DELETE r
     
   - 步骤 3：删除旧节点
     context.deleteNode.accept(nodeId)
     ↓ 执行
     repository.deleteNode(nodeId)
     ↓ 执行
     Neo4j: MATCH (n {id: $id}) DETACH DELETE n
     
   - 步骤 4：解析新文件
     javaCodeParser.parseFile(filePath)
     ↓ 执行
     JDT 解析
     
   - 步骤 5：保存新节点
     context.saveFunction.accept(function)
     ↓ 执行
     repository.saveFunction(function)
     ↓ 执行
     Neo4j: MERGE (f:Function {id: $id}) SET f.name = $name, ...
     
   - 步骤 6：重建调用关系
     context.saveCallRelationship.accept(rel)
     ↓ 执行
     repository.saveCallRelationship(rel)
     ↓ 执行
     Neo4j: MATCH (from {id: $fromId}) MERGE (to {id: $toId}) CREATE (from)-[:CALLS]->(to)
   ↓
5. 返回 ChangeHandleResult
```

---

## 核心设计原则

### 1. 分层解耦

```
领域层：
  - 定义业务逻辑
  - 不依赖技术实现
  - 通过函数表达式访问数据

应用层：
  - 提供数据访问函数
  - 调用领域层
  - 编排外部服务

基础设施层：
  - 实现数据访问
  - 连接外部系统（Neo4j）
```

---

### 2. 函数式设计

```java
// 领域层定义需要的能力（协议）
FunctionBundle {
    Function<String, List<String>> findWhoCallsMe;
    Consumer<String> deleteNode;
}

// 应用层提供具体实现
context.setFindWhoCallsMe(filePath -> repository.findWhoCallsMe(filePath));
context.setDeleteNode(nodeId -> repository.deleteNode(nodeId));

// 领域层使用
List<String> deps = context.getFindWhoCallsMe().apply(filePath);
context.getDeleteNode().accept(nodeId);
```

**优势：**
- 完全解耦
- 易于测试
- 灵活扩展

---

### 3. 增量更新策略

```
直接变更（PRIMARY）：
  1. 查找依赖文件
  2. 删除所有文件的出边
  3. 删除当前文件的节点
  4. 解析当前文件
  5. 保存新节点
  6. 重建所有文件的调用关系

级联变更（CASCADE）：
  1. 删除当前文件的出边
  2. 重建当前文件的调用关系
  （不删除节点，不查询依赖）
```

**关键：**
- 级联变更不删除节点 → 节点 ID 保持稳定
- 级联变更不查询依赖 → 避免无限循环

---

### 4. 全限定名作为 ID

```
节点 ID = 全限定名

例如：
  - 类：com.example.UserService
  - 方法：com.example.UserService.findUser(String):User

优势：
  - 不需要查询 ID
  - ID 天然稳定
  - 语义清晰
```

---

### 5. 占位符节点

```
对于外部依赖（JDK、第三方库），创建占位符节点：

CodeFunction {
    id: "java.lang.String.valueOf(int):String",
    isPlaceholder: true,
    isExternal: true
}

使用 MERGE 自动创建：
  MERGE (to:Function {id: $toId})
  ON CREATE SET to.isPlaceholder = true
```

---

## 技术栈

- **Java 21**
- **Spring Boot 3.4.5**
- **Maven**
- **JDT (Eclipse Java Development Tools)** - 代码解析
- **Neo4j** - 图数据库（待集成）
- **Lombok** - 简化代码

---

## 性能指标（预期）

### 单文件更新

```
文件大小：50 个方法
依赖文件：5 个

性能：
  - 查询依赖：10ms
  - 删除出边：20ms
  - 删除节点：10ms
  - 解析文件：50-100ms
  - 保存节点：20ms
  - 重建调用关系（6 个文件）：300-600ms
  
总计：400-760ms
```

### 批量更新

```
10 个文件同时更新：
  - 串行处理：4-8 秒
  - 并行处理：1-2 秒（待实现）
```

---

## 扩展性

### 支持其他语言

当前设计已经考虑了多语言支持：

```java
// 领域模型是语言无关的
CodeFunction {
    language: "java",  // 可以是 "python", "go", "javascript"
    // ...
}

// 可以添加其他语言的解析器
public interface CodeParser {
    CodeGraph parseFile(String filePath);
}

// 实现
@Component
public class PythonCodeParser implements CodeParser {
    // 使用 Python AST 解析
}

@Component
public class GoCodeParser implements CodeParser {
    // 使用 Go AST 解析
}
```

---

## 总结

当前已完成核心的增量更新逻辑设计和实现，包括：

1. ✅ 完整的领域模型
2. ✅ 增量更新流程
3. ✅ 函数式设计（解耦）
4. ✅ 编译通过

下一步需要实现：

1. ❌ Neo4j Repository
2. ❌ JDT 项目配置
3. ❌ Git Webhook 集成
4. ❌ 集成测试

整体架构清晰、可扩展、易维护！🎉

