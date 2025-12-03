# Code Graph Engine

代码图谱引擎 - 基于 JDT 的 Java 代码分析工具

## 项目结构

```
code-graph/
├── pom.xml                                    # 父 POM
└── code-graph-engine/                         # 核心引擎模块
    ├── pom.xml
    └── src/main/java/com/poseidon/codegraph/engine/
        ├── CodeGraphEngineApplication.java    # 启动类
        │
        ├── domain/                            # 领域层
        │   ├── model/                         # 领域模型
        │   │   ├── CodeNode.java              # 代码节点基类
        │   │   ├── CodePackage.java           # 代码包
        │   │   ├── CodeUnit.java              # 代码单元（类/接口/枚举）
        │   │   ├── CodeFunction.java          # 代码函数（方法）
        │   │   ├── CallRelationship.java      # 调用关系
        │   │   ├── CodeGraph.java             # 代码图
        │   │   ├── FunctionBundle.java  # 执行上下文 ⭐
        │   │   └── event/                     # 领域事件
        │   │       ├── CodeChangeEvent.java   # 代码变更事件
        │   │       └── ChangeType.java        # 变更类型
        │   │
        │   ├── parser/                        # 代码解析（领域能力）
        │   │   ├── CodeParser.java            # 解析器接口
        │   │   ├── JavaCodeParser.java        # Java 解析器实现
        │   │   └── jdt/
        │   │       └── JdtParser.java         # JDT 工具类
        │   │
        │   ├── service/                       # 领域服务
        │   │   ├── FileChangeHandler.java     # 增量更新核心逻辑 ⭐
        │   │   └── ChangeHandleResult.java    # 处理结果
        │   │
        │   └── repository/                    # 仓储接口（协议）
        │       └── CodeGraphRepository.java   # 数据访问协议
        │
        ├── application/                       # 应用层
        │   └── service/
        │       ├── CodeParseService.java      # 代码解析服务
        │       └── IncrementalUpdateService.java  # 增量更新服务 ⭐
        │
        └── infrastructure/                    # 基础设施层
            └── persistence/                   # 数据持久化（待实现）
                └── neo4j/                     # Neo4j 实现
```

## 核心概念

### 领域模型

- **CodePackage**: 代码包，表示 Java 的 package
- **CodeUnit**: 代码单元，表示类/接口/枚举/注解
- **CodeFunction**: 代码函数，表示方法
- **CallRelationship**: 调用关系，表示方法之间的调用
- **CodeGraph**: 代码图，包含所有上述元素

### 架构设计

本项目采用 **DDD（领域驱动设计）+ 函数式编程** 的架构：

#### 1. 领域层（Domain Layer）
- **职责**：定义核心业务逻辑，不依赖任何技术实现
- **FileChangeHandler**：增量更新的核心逻辑
  - 接收变更事件（CodeChangeEvent）
  - 接收执行上下文（FunctionBundle）
  - 编排处理流程（查询依赖、删除节点、解析文件、重建关系）
- **FunctionBundle**：函数表达式集合
  - 定义领域层需要的所有操作（查询、删除、保存）
  - 由应用层提供具体实现

#### 2. 应用层（Application Layer）
- **职责**：构建执行上下文，提供数据访问函数
- **IncrementalUpdateService**：
  - 构建 FunctionBundle
  - 提供数据访问函数的具体实现（Lambda 表达式）
  - 调用领域层处理

#### 3. 变更类型
- **PRIMARY**：直接变更（来自 Git）
  - 需要查询依赖、删除节点、解析文件、触发级联
- **CASCADE**：级联变更（来自依赖）
  - 只需要删除出边、重建调用关系

### 数据模型设计

#### 节点设计
- **不使用 File 节点**：文件路径作为节点属性（`filePath`）
- **使用全限定名作为节点 ID**：
  - `CodeUnit.id = "com.example.UserService"`
  - `CodeFunction.id = "com.example.UserService.findUser(String):User"`
- **占位符节点**：对于外部依赖（JDK、第三方库），创建占位符节点
  - `isPlaceholder = true`
  - `isExternal = true`

#### 关系设计
- **CALLS**：方法调用关系
- **使用 MERGE 自动创建占位符**：如果目标节点不存在，自动创建

### 增量更新流程

```
1. Git 事件 → 文件变更
   ↓
2. 应用层：构建 FunctionBundle
   functions.setFindWhoCallsMe(filePath -> repository.findWhoCallsMe(filePath))
   functions.setDeleteNode(nodeId -> repository.deleteNode(nodeId))
   ...
   ↓
3. 领域层：编排处理流程
   - 查找谁依赖我：dependentFiles = functions.findWhoCallsMe(filePath)
   - 收集需要重建的文件：[filePath] + dependentFiles
   - 删除出边：functions.deleteFileOutgoingCalls(file)
   - 删除旧节点：functions.deleteNode(nodeId)
   - 解析新文件：javaCodeParser.parseFile(filePath)
   - 保存新节点：functions.saveUnit(unit)
   - 重建调用关系：functions.saveCallRelationship(rel)
   ↓
4. 返回处理结果
```

## 快速开始

### 1. 构建项目

```bash
cd code-graph
mvn clean package
```

### 2. 运行测试

```bash
mvn test
```

### 3. 使用示例

#### 增量更新（核心功能）⭐

```java
@Autowired
private IncrementalUpdateService incrementalUpdateService;

// 处理文件修改
ChangeHandleResult result = incrementalUpdateService.handleFileModified(
    "src/main/java/com/example/UserService.java",
    "Git commit: fix bug"
);

// 查看结果
System.out.println("处理结果: " + result.getSummary());
System.out.println("处理文件数: " + result.getProcessedFiles());
System.out.println("新增节点: " + result.getAddedUnits() + " 个类, " 
    + result.getAddedFunctions() + " 个方法");
System.out.println("删除节点: " + result.getDeletedNodes());
System.out.println("更新关系: " + result.getUpdatedCallRelationships());
System.out.println("耗时: " + result.getDurationMs() + "ms");
```

#### 解析单个文件

```java
@Autowired
private CodeParseService codeParseService;

// 解析单个文件
CodeGraph graph = codeParseService.parseJavaFile("/path/to/File.java");
List<CodeUnit> units = graph.getUnitsAsList();
List<CodeFunction> functions = graph.getFunctionsAsList();
```

## 技术栈

- **Java 21**
- **Spring Boot 3.4.5**
- **Eclipse JDT Core 3.32.0** - Java 代码解析
- **Lombok** - 简化代码
- **Maven** - 构建工具
- **Neo4j**（计划） - 图数据库

## 核心特性

- ✅ **增量更新**：只解析变更的文件，自动处理依赖关系
- ✅ **级联更新**：自动重建受影响文件的调用关系
- ✅ **占位符节点**：自动处理外部依赖（JDK、第三方库）
- ✅ **函数式设计**：领域层通过函数表达式解耦
- ✅ **DDD 架构**：清晰的分层设计

## 待实现

- [ ] Neo4j 持久化实现（CodeGraphRepository）
- [ ] JDT 项目上下文配置（classpath、sourcepath）
- [ ] Git Webhook 集成
- [ ] 完整的测试用例
- [ ] REST API
- [ ] 支持更多语言（Python、Go、JavaScript）

## License

MIT
