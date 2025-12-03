# 最终架构设计

## 架构分层

```
┌─────────────────────────────────────────────────────────────┐
│                    应用层 (Application)                      │
│                    - 使用 Spring                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ IncrementalUpdateService (@Service)                  │   │
│  │  - 构建 CodeChangeEvent（包含项目上下文）            │   │
│  │  - 构建 FunctionBundle（函数表达式）      │   │
│  │  - 通过 Repository 提供数据访问                      │   │
│  │  - 负责 DO ↔ 领域模型的转换                          │   │
│  └────────┬─────────────────────────────────────────────┘   │
│           │                                                  │
│  ┌────────▼─────────────────────────────────────────────┐   │
│  │ CodeGraphRepository (接口)                           │   │
│  │  - 使用 DO 模型（xxxDO.java）                        │   │
│  │  - 定义数据访问协议                                  │   │
│  └──────────────────────────────────────────────────────┘   │
│           │                                                  │
│  ┌────────▼─────────────────────────────────────────────┐   │
│  │ DO 模型 (model/)                                     │   │
│  │  - CodePackageDO                                     │   │
│  │  - CodeUnitDO                                        │   │
│  │  - CodeFunctionDO                                    │   │
│  │  - CallRelationshipDO                                │   │
│  └──────────────────────────────────────────────────────┘   │
│           │                                                  │
│  ┌────────▼─────────────────────────────────────────────┐   │
│  │ Converter (converter/)                               │   │
│  │  - CodeGraphConverter                                │   │
│  │  - DO ↔ 领域模型转换                                 │   │
│  └──────────────────────────────────────────────────────┘   │
└───────────┼──────────────────────────────────────────────────┘
            │ 调用（传入 event + context）
            ↓
┌─────────────────────────────────────────────────────────────┐
│                    领域层 (Domain)                           │
│                    - 纯 Java，无框架依赖                     │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ FileChangeHandler (无状态)                           │   │
│  │  - 编排增量更新流程                                  │   │
│  │  - 通过 context 访问数据（函数表达式）               │   │
│  │  - 调用 JavaCodeParser 解析文件                      │   │
│  └────────┬─────────────────────────────────────────────┘   │
│           │                                                  │
│  ┌────────▼─────────────────────────────────────────────┐   │
│  │ JavaCodeParser (无状态)                              │   │
│  │  - 解析单个 Java 文件                                │   │
│  │  - 调用 JdtParser                                    │   │
│  │  - 构建领域模型（CodeUnit、CodeFunction 等）         │   │
│  └────────┬─────────────────────────────────────────────┘   │
│           │                                                  │
│  ┌────────▼─────────────────────────────────────────────┐   │
│  │ JdtParser (无状态)                                   │   │
│  │  - 调用 JDT API                                      │   │
│  │  - 返回 CompilationUnit                              │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 领域模型 (model/)                                    │   │
│  │  - CodePackage, CodeUnit, CodeFunction              │   │
│  │  - CallRelationship, CodeGraph                       │   │
│  │  - CodeChangeEvent, ChangeType                       │   │
│  │  - FunctionBundle                         │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
            ↑
            │ 实现（使用 DO）
            ↓
┌─────────────────────────────────────────────────────────────┐
│                  基础设施层 (Infrastructure)                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ Neo4jCodeGraphRepository (待实现)                    │   │
│  │  - 实现 CodeGraphRepository 接口                     │   │
│  │  - 使用 DO 模型                                      │   │
│  │  - 操作 Neo4j 数据库                                 │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 核心设计原则

### 1. 领域层完全解耦

- ✅ 不依赖 Spring（无 `@Component`、`@Service` 等注解）
- ✅ 不依赖应用层的 DO 模型
- ✅ 所有类都是无状态的（纯函数）
- ✅ 通过函数表达式访问数据（`FunctionBundle`）

### 2. 应用层负责协调

- ✅ 定义 DO 模型（`xxxDO.java`）
- ✅ 定义 Repository 接口（使用 DO）
- ✅ 负责 DO ↔ 领域模型的转换（`Converter`）
- ✅ 构建执行上下文（提供数据访问函数）
- ✅ 管理项目上下文（`projectRoot`、`classpathEntries`、`sourcepathEntries`）

### 3. 项目上下文作为参数传入

所有需要解析的地方都需要传入：
- `projectRoot` - 项目根目录
- `classpathEntries` - 依赖的 jar 包路径
- `sourcepathEntries` - 源代码目录

---

## 数据流

### 完整的增量更新流程

```
1. Git Webhook 通知文件变更
   ↓
2. 应用层（IncrementalUpdateService）
   - 创建 CodeChangeEvent
     event.setProjectRoot("/path/to/project")
     event.setClasspathEntries([...])
     event.setSourcepathEntries([...])
     event.setOldFileIdentifier("UserService.java")
     event.setNewFilePath("UserService.java")
   
   - 构建 FunctionBundle
     context.setFindWhoCallsMe(filePath -> 
         repository.findWhoCallsMe(filePath)  // 返回 DO
             .stream().map(Converter::toDomain)  // 转换为领域模型
     )
     context.setDeleteNode(nodeId -> 
         repository.deleteNode(nodeId)
     )
     context.setSaveFunction(function -> 
         repository.saveFunction(Converter.toDO(function))  // 转换为 DO
     )
   
   - 调用领域层
     codeChangeService.handle(event, context)
   ↓
3. 领域层（FileChangeHandler）
   - 查询依赖：dependentFiles = context.findWhoCallsMe(filePath)
   - 删除出边：context.deleteFileOutgoingCalls(filePath)
   - 删除节点：context.deleteNode(nodeId)
   - 解析文件：javaCodeParser.parseFile(
       event.getProjectRoot(),
       filePath,
       event.getClasspathEntries(),
       event.getSourcepathEntries()
     )
   - 保存节点：context.saveFunction(function)
   - 保存关系：context.saveCallRelationship(rel)
   ↓
4. 应用层执行数据访问
   - repository.findWhoCallsMe(filePath)
   - repository.deleteNode(nodeId)
   - repository.saveFunction(functionDO)
   ↓
5. 基础设施层（Neo4j）
   - 执行 Cypher 查询
   - 执行 Cypher 删除
   - 执行 Cypher 保存
```

---

## 关键组件说明

### CodeParser（领域层）

**职责：** 解析单个 Java 文件

**输入：**
- `projectRoot` - 项目根目录
- `filePath` - 文件路径
- `classpathEntries` - 依赖路径
- `sourcepathEntries` - 源码路径

**输出：**
- `CodeGraph` - 包含该文件的类、方法、调用关系

**实现：**
- `JavaCodeParser` - 使用 JDT 解析 Java 代码

**核心逻辑：**
```java
CodeGraph graph = parser.parseFile(projectRoot, filePath, classpath, sourcepath);
// graph 包含：
//   - packages: 包信息
//   - units: 类/接口/枚举
//   - functions: 方法
//   - relationships: 方法调用关系
```

---

### FileChangeHandler（领域层）

**职责：** 编排增量更新流程

**输入：**
- `CodeChangeEvent` - 变更事件（包含项目上下文）
- `FunctionBundle` - 执行上下文（函数表达式）

**输出：**
- `ChangeHandleResult` - 处理结果

**核心逻辑：**
1. 查询谁依赖我
2. 收集需要重建的文件
3. 删除出边
4. 删除旧节点
5. 解析新文件
6. 保存新节点
7. 重建调用关系

---

### IncrementalUpdateService（应用层）

**职责：** 提供增量更新的入口

**输入：**
- `projectRoot` - 项目根目录
- `filePath` - 文件路径
- `classpathEntries` - 依赖路径
- `sourcepathEntries` - 源码路径

**输出：**
- `ChangeHandleResult` - 处理结果

**核心逻辑：**
1. 构建 `CodeChangeEvent`
2. 构建 `FunctionBundle`（提供数据访问函数）
3. 调用 `FileChangeHandler.handle()`

---

### CodeGraphRepository（应用层接口）

**职责：** 定义数据访问协议

**使用：** DO 模型（`xxxDO.java`）

**方法：**
- `findWhoCallsMe(filePath)` - 查询依赖
- `findUnitsByFilePath(filePath)` - 查询类
- `findFunctionsByFilePath(filePath)` - 查询方法
- `findFunctionByQualifiedName(name)` - 查询方法（通过全限定名）
- `deleteFileOutgoingCalls(filePath)` - 删除出边
- `deleteNode(nodeId)` - 删除节点
- `saveUnit(unitDO)` - 保存类
- `saveFunction(functionDO)` - 保存方法
- `saveCallRelationship(relDO)` - 保存调用关系

**实现：** 基础设施层（Neo4j - 待实现）

---

## 线上服务场景

### 典型流程

```
1. 用户配置 Git 仓库
   POST /api/project/init
   {
     "repoUrl": "https://github.com/user/project.git",
     "branch": "main"
   }
   
2. 服务端处理
   - Clone 项目到服务器：/data/projects/{projectId}/
   - 执行 mvn compile（下载依赖）
   - 解析 classpath（从 Maven）
   - 全量解析项目（初始化图谱）
   
3. Git Webhook 通知文件变更
   POST /api/webhook/git/push
   {
     "projectId": "xxx",
     "commits": [{
       "modified": ["src/main/java/com/example/UserService.java"]
     }]
   }
   
4. 服务端处理
   - 拉取最新代码：git pull
   - 获取项目上下文：
     projectRoot = "/data/projects/{projectId}/"
     classpathEntries = [...] // 从缓存或重新解析
     sourcepathEntries = [...] // src/main/java 等
   
   - 调用增量更新：
     updateService.handleFileModified(
       projectRoot,
       projectRoot + "src/main/java/com/example/UserService.java",
       classpathEntries,
       sourcepathEntries
     )
   
5. 返回结果
   {
     "success": true,
     "processedFiles": 3,
     "addedFunctions": 5,
     "deletedNodes": 2,
     "updatedCallRelationships": 15,
     "durationMs": 450
   }
```

---

## 核心设计决策

### 1. 领域层无状态、无框架依赖

**原因：**
- 领域层是纯业务逻辑，不应该依赖技术框架
- 易于测试（不需要启动 Spring）
- 易于迁移（可以用在其他框架中）

**实现：**
- 所有类都是纯 POJO
- 所有方法都是纯函数（无副作用）
- 所有依赖都通过参数传入

---

### 2. 项目上下文作为参数传入

**原因：**
- 支持多项目（每个项目有自己的上下文）
- 无状态（不在对象中维护状态）
- 线程安全

**实现：**
- `projectRoot`、`classpathEntries`、`sourcepathEntries` 都通过参数传入
- 存储在 `CodeChangeEvent` 中
- 由应用层管理和缓存

---

### 3. 应用层使用 DO 模型

**原因：**
- 应用层和领域层解耦
- 领域模型可以自由演化
- DO 模型和数据库结构对应

**实现：**
- Repository 接口使用 DO
- Converter 负责转换
- 应用层在构建执行上下文时进行转换

---

### 4. 函数式设计

**原因：**
- 领域层不依赖 Repository 接口
- 完全解耦
- 易于测试（可以 Mock 函数）

**实现：**
- `FunctionBundle` 包含所有数据访问函数
- 应用层提供具体实现（Lambda 表达式）
- 领域层只调用函数，不知道具体实现

---

## 目录结构

```
code-graph-engine/src/main/java/com/poseidon/codegraph/engine/
├── domain/                                    # 领域层（纯 Java）
│   ├── model/                                 # 领域模型
│   │   ├── CodeNode.java                      # 节点基类
│   │   ├── CodePackage.java                   # 包
│   │   ├── CodeUnit.java                      # 类/接口/枚举
│   │   ├── CodeFunction.java                  # 方法
│   │   ├── CallRelationship.java              # 调用关系
│   │   ├── CodeGraph.java                     # 解析结果容器
│   │   ├── FunctionBundle.java     # 执行上下文
│   │   └── event/
│   │       ├── CodeChangeEvent.java           # 变更事件
│   │       └── ChangeType.java                # 变更类型
│   │
│   ├── service/                               # 领域服务
│   │   ├── FileChangeHandler.java             # 增量更新核心逻辑
│   │   └── ChangeHandleResult.java            # 处理结果
│   │
│   └── parser/                                # 代码解析
│       ├── CodeParser.java                    # 解析器接口
│       ├── JavaCodeParser.java                # Java 解析器
│       └── jdt/
│           └── JdtParser.java                 # JDT 工具类
│
├── application/                               # 应用层（使用 Spring）
│   ├── model/                                 # DO 模型
│   │   ├── CodePackageDO.java
│   │   ├── CodeUnitDO.java
│   │   ├── CodeFunctionDO.java
│   │   └── CallRelationshipDO.java
│   │
│   ├── converter/                             # 转换器
│   │   └── CodeGraphConverter.java            # DO ↔ 领域模型
│   │
│   ├── repository/                            # Repository 接口
│   │   └── CodeGraphRepository.java           # 使用 DO
│   │
│   └── service/                               # 应用服务
│       └── IncrementalUpdateService.java      # 增量更新入口
│
└── infrastructure/                            # 基础设施层（待实现）
    └── persistence/
        └── neo4j/
            └── Neo4jCodeGraphRepository.java  # Repository 实现
```

---

## 使用示例

### 1. 初始化项目

```java
// 1. Clone 项目到服务器
String projectRoot = "/data/projects/my-project/";
gitService.clone("https://github.com/user/project.git", projectRoot);

// 2. 构建项目（下载依赖）
mavenService.build(projectRoot);

// 3. 解析 classpath
String[] classpathEntries = mavenService.resolveClasspath(projectRoot);
String[] sourcepathEntries = new String[] {
    projectRoot + "src/main/java",
    projectRoot + "src/test/java"
};

// 4. 缓存项目上下文（应用层维护）
projectContextCache.put(projectId, new ProjectContext(
    projectRoot, classpathEntries, sourcepathEntries
));
```

### 2. 处理文件变更

```java
@Autowired
private IncrementalUpdateService updateService;

// 从缓存获取项目上下文
ProjectContext ctx = projectContextCache.get(projectId);

// 处理文件修改
ChangeHandleResult result = updateService.handleFileModified(
    ctx.getProjectRoot(),
    ctx.getProjectRoot() + "src/main/java/com/example/UserService.java",
    ctx.getClasspathEntries(),
    ctx.getSourcepathEntries()
);

// 查看结果
System.out.println("成功: " + result.isSuccess());
System.out.println("处理文件数: " + result.getProcessedFiles());
System.out.println("耗时: " + result.getDurationMs() + "ms");
```

---

## 下一步实现

### 1. 实现 Neo4j Repository（优先级：高）

```java
@Repository
public class Neo4jCodeGraphRepository implements CodeGraphRepository {
    
    @Autowired
    private Neo4jClient neo4jClient;
    
    @Override
    public List<String> findWhoCallsMe(String targetFilePath) {
        // Cypher: MATCH (caller)-[:CALLS]->(callee {filePath: $path})
        //         RETURN DISTINCT caller.filePath
    }
    
    @Override
    public void saveFunction(CodeFunctionDO function) {
        // Cypher: MERGE (f:Function {id: $id})
        //         SET f.name = $name, f.filePath = $filePath, ...
    }
    
    // ... 其他方法
}
```

### 2. 实现 Maven 依赖解析（优先级：高）

```java
@Service
public class MavenService {
    
    public String[] resolveClasspath(String projectRoot) {
        // 执行: mvn dependency:build-classpath -DincludeScope=compile
        // 解析输出，返回所有 jar 包路径
    }
}
```

### 3. 实现 Git 服务（优先级：中）

```java
@Service
public class GitService {
    
    public void clone(String repoUrl, String targetDir) {
        // 执行: git clone
    }
    
    public void pull(String projectRoot) {
        // 执行: git pull
    }
}
```

### 4. 实现项目上下文缓存（优先级：中）

```java
@Service
public class ProjectContextService {
    
    private Map<String, ProjectContext> cache = new ConcurrentHashMap<>();
    
    public ProjectContext getOrCreate(String projectId) {
        // 从缓存获取或创建新的项目上下文
    }
}
```

---

## 总结

✅ **架构完全符合要求：**
- 领域层纯净（无框架依赖）
- 应用层使用 DO 模型
- 项目上下文作为参数传入
- 支持线上服务场景（Git 下载、多项目）

✅ **编译成功**

❌ **待实现：**
- Neo4j Repository
- Maven 依赖解析
- Git 服务
- 项目上下文管理

