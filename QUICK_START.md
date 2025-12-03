# 快速开始指南

## 已完成的功能

### ✅ 领域层

1. **数据模型**
   - `CodePackage` - 代码包
   - `CodeUnit` - 代码单元（类、接口、枚举）
   - `CodeFunction` - 代码函数（方法）
   - `CallRelationship` - 调用关系
   - `CodeGraph` - 代码图谱

2. **事件模型**
   - `CodeChangeEvent` - 代码变更事件
   - `ChangeType` - 变更类型（PRIMARY/CASCADE）

3. **执行上下文**
   - `FunctionBundle` - 执行上下文（函数表达式）

4. **领域服务**
   - `FileChangeHandler` - 代码变更处理服务
   - `ChangeHandleResult` - 处理结果

5. **解析器**
   - `JavaCodeParser` - Java 代码解析器（使用 JDT）
   - `JdtParser` - JDT 工具类

6. **仓储接口**
   - `CodeGraphRepository` - 代码图谱仓储接口（协议）

---

### ✅ 应用层

1. **应用服务**
   - `IncrementalUpdateService` - 增量更新服务
   - `CodeParseService` - 代码解析服务

---

## 待实现的功能

### ❌ 基础设施层（优先级：高）

1. **Neo4j Repository 实现**
   ```java
   @Repository
   public class Neo4jCodeGraphRepository implements CodeGraphRepository {
       // 实现所有接口方法
   }
   ```

2. **Neo4j 配置**
   ```yaml
   spring:
     neo4j:
       uri: bolt://localhost:7687
       authentication:
         username: neo4j
         password: password
   ```

3. **索引创建**
   ```cypher
   CREATE INDEX function_filePath FOR (f:Function) ON (f.filePath)
   CREATE INDEX function_id FOR (f:Function) ON (f.id)
   CREATE INDEX codeunit_filePath FOR (u:CodeUnit) ON (u.filePath)
   CREATE INDEX codeunit_id FOR (u:CodeUnit) ON (u.id)
   ```

---

### ❌ JDT 项目配置（优先级：高）

1. **项目环境配置**
   ```java
   @Component
   public class JdtProjectConfig {
       public ASTParser createParser(String projectRoot) {
           // 配置 classpath（项目依赖 + JDK）
           // 配置 sourcepath（源代码目录）
       }
   }
   ```

2. **Maven 依赖解析**
   ```java
   @Service
   public class MavenDependencyResolver {
       public List<String> resolveDependencies(String projectRoot) {
           // 执行 mvn dependency:build-classpath
           // 返回所有 jar 包路径
       }
   }
   ```

---

### ❌ 接口层（优先级：中）

1. **Git Webhook 控制器**
   ```java
   @RestController
   @RequestMapping("/api/webhook/git")
   public class GitWebhookController {
       @PostMapping("/push")
       public WebhookResponse handlePush(@RequestBody GitPushPayload payload) {
           // 处理 Git Push 事件
       }
   }
   ```

2. **手动更新控制器**
   ```java
   @RestController
   @RequestMapping("/api/update")
   public class UpdateController {
       @PostMapping("/file")
       public ChangeHandleResult updateFile(@RequestParam String filePath) {
           // 手动触发文件更新
       }
   }
   ```

---

### ❌ 项目管理（优先级：中）

1. **项目初始化服务**
   ```java
   @Service
   public class ProjectManagementService {
       public void initProject(String repoUrl, String projectId) {
           // Clone 项目
           // 构建项目
           // 全量解析
       }
       
       public void updateProject(String projectId, List<String> changedFiles) {
           // 增量更新
       }
   }
   ```

---

## 快速验证

### 1. 编译项目

```bash
cd /Users/joker/code-graph
mvn clean compile
```

**状态：** ✅ 编译成功

---

### 2. 查看已创建的文件

```bash
# 领域层
code-graph-engine/src/main/java/com/poseidon/codegraph/engine/domain/
├── model/
│   ├── CodeNode.java
│   ├── CodePackage.java
│   ├── CodeUnit.java
│   ├── CodeFunction.java
│   ├── CallRelationship.java
│   ├── CodeGraph.java
│   ├── FunctionBundle.java  ← 新增
│   └── event/
│       ├── CodeChangeEvent.java
│       └── ChangeType.java  ← 新增
│
├── service/
│   ├── FileChangeHandler.java  ← 新增
│   └── ChangeHandleResult.java  ← 新增
│
├── parser/
│   ├── CodeParser.java
│   ├── JavaCodeParser.java
│   └── jdt/
│       └── JdtParser.java
│
└── repository/
    └── CodeGraphRepository.java

# 应用层
code-graph-engine/src/main/java/com/poseidon/codegraph/engine/application/
└── service/
    ├── IncrementalUpdateService.java  ← 新增
    └── CodeParseService.java

# 测试
code-graph-engine/src/test/java/com/poseidon/codegraph/engine/
└── IncrementalUpdateServiceTest.java  ← 新增

# 文档
code-graph-engine/
├── INCREMENTAL_UPDATE.md  ← 新增
└── USAGE_EXAMPLE.md  ← 新增

DESIGN_SUMMARY.md  ← 新增
```

---

## 下一步行动

### 第一步：实现 Neo4j Repository（必须）

这是运行系统的前提条件。

**需要做的：**
1. 添加 Neo4j 依赖到 `pom.xml`
2. 配置 Neo4j 连接
3. 实现 `Neo4jCodeGraphRepository`
4. 创建索引

---

### 第二步：配置 JDT 项目环境（必须）

这是解析代码的前提条件。

**需要做的：**
1. 实现 `JdtProjectConfig`
2. 实现 `MavenDependencyResolver`
3. 配置项目根目录管理

---

### 第三步：集成测试（推荐）

验证整个流程是否正确。

**需要做的：**
1. 准备测试项目
2. 初始化项目（全量解析）
3. 模拟文件变更
4. 验证增量更新结果

---

## 当前状态

- ✅ 领域层设计完成
- ✅ 应用层设计完成
- ✅ 编译通过
- ❌ 基础设施层未实现（需要实现 Repository）
- ❌ 接口层未实现（需要实现 Controller）
- ❌ 集成测试未完成

---

## 联系方式

如有问题，请查看：
- `DESIGN_SUMMARY.md` - 设计总结
- `INCREMENTAL_UPDATE.md` - 增量更新设计
- `USAGE_EXAMPLE.md` - 使用示例

