# 端点解析框架 (Endpoint Parsing Framework)

## 概述

基于 EPR (Endpoint Parse Rule) 的端点解析框架，通过声明式规则文件描述如何从 Java 源代码中提取端点信息（HTTP API、Kafka Topic、Redis Key 等）。

## 核心设计

### 1. 分层架构

```
第 1 层: Scope Filter (作用域过滤)
  - 根据包路径、文件名快速过滤规则
  - 支持通配符：** 任意层级，* 单层

第 2 层: Context Manager (上下文管理)
  - 追踪 Router 等对象及其属性
  - 支持嵌套和链式调用

第 3 层: Node Locator (节点定位)
  - 在 AST 中定位目标节点
  - 支持注解、方法名、接收者等条件

第 4 层: Value Extractor (值提取)
  - 从 AST 节点提取信息
  - 自动追踪变量、字段、拼接表达式

第 5 层: Endpoint Builder (端点构建)
  - 组装 CodeEndpoint 对象
  - 生成关系信息
```

### 2. 核心组件

```
EndpointParsingService
  ├── EprRuleLoader          # 加载 .epr 规则文件
  ├── ScopeFilter            # 作用域过滤器
  ├── SimpleEprEngine        # EPR 执行引擎
  └── UniversalValueTracer   # 通用值追踪器
```

## 已实现功能

### ✅ 核心能力

1. **EPR 规则加载**
   - 从 YAML 文件加载规则
   - 支持内置和自定义规则
   - 优先级排序

2. **作用域过滤**
   - 包路径模式匹配（支持 ** 和 *）
   - 文件名模式匹配
   - 类注解检查

3. **值追踪**
   - 字面量直接提取
   - 变量追踪（局部变量、字段）
   - 字符串拼接（多层嵌套）
   - 防循环引用和深度保护

4. **端点提取**
   - HTTP 入口（Controller）
   - HTTP 出口（RestTemplate）
   - 支持注解解析
   - 支持方法调用参数解析

### ✅ 已支持的场景

#### 场景 1: Spring MVC Controller

**源代码**:
```java
@RestController
@RequestMapping("/api/users")
public class UserController {
    
    @GetMapping("/{id}")
    public User getUser(@PathVariable Long id) {
        // ...
    }
}
```

**解析结果**:
```
CodeEndpoint {
  type: HTTP
  direction: inbound
  method: GET
  path: /api/users/{id}
}
```

#### 场景 2: RestTemplate 出口

**源代码**:
```java
public class OrderService {
    private String orderServiceUrl = "http://order-service";
    
    public Order getOrder(Long id) {
        String url = orderServiceUrl + "/api/orders/" + id;
        return restTemplate.getForObject(url, Order.class);
    }
}
```

**解析结果**:
```
CodeEndpoint {
  type: HTTP
  direction: outbound
  method: GET
  path: http://order-service/api/orders/{id}
  parseLevel: PARTIAL
}
```

#### 场景 3: 多层变量拼接

**源代码**:
```java
String basePath = "/api";
String resource = basePath + "/orders";
String fullPath = resource + "/" + id;
String url = serviceUrl + fullPath;
restTemplate.get(url);
```

**解析结果**: ✅ 自动追踪所有变量，组合完整路径

## EPR 规则文件格式

### 基本结构

```yaml
name: "规则名称"
description: "规则描述"
version: "1.0"
enabled: true
priority: 100

# 1. 作用域
scope:
  packageIncludes:
    - "**.controller.**"
  packageExcludes:
    - "**.test.**"

# 2. 定位
locate:
  nodeType: "MethodDeclaration"
  where:
    - hasAnnotation:
        nameMatches: "@.*Mapping$"

# 3. 提取
extract:
  httpMethod:
    from: "methodName"
    transform: "toUpperCase"
  
  path:
    from: "method.annotation[@GetMapping].attribute[value]"
    trace: "auto"

# 4. 构建
build:
  endpointType: "HTTP"
  direction: "inbound"
  path: "${path}"
```

### 路径表达式语法

```yaml
# 获取参数
from: "argument[0]"           # 第 1 个参数
from: "argument[1]"           # 第 2 个参数

# 获取注解属性
from: "method.annotation[@GetMapping].attribute[value]"
from: "class.annotation[@RequestMapping].attribute[path]"

# 获取方法名
from: "methodName"

# 组合多个值
combine:
  sources:
    - source: "${basePath}"
    - source: "${methodPath}"
  normalize: true
```

## 内置规则

### 1. spring-mvc-inbound.epr
- 解析 Spring MVC Controller 的 HTTP 入口
- 支持 @RequestMapping, @GetMapping 等
- 自动组合类和方法级别的路径

### 2. rest-template-outbound.epr
- 解析 RestTemplate 的 HTTP 出口调用
- 支持 getForObject, postForObject 等
- 自动追踪 URL 参数的值

### 3. router-nested.epr
- 解析带基础路径的 Router 对象（预留）
- 支持嵌套 Router
- 自动组合基础路径和子路径

## 使用示例

### 在解析流程中集成

```java
@Service
public class MyParser {
    
    @Autowired
    private EndpointParsingService endpointParsingService;
    
    public void parseFile(String filePath) {
        // 1. 解析 AST
        CompilationUnit cu = ...;
        
        // 2. 解析端点
        List<CodeEndpoint> endpoints = endpointParsingService.parseEndpoints(
            cu,
            "com.example.controller",  // 包名
            "UserController.java",      // 文件名
            "/project/src/..."          // 项目文件路径
        );
        
        // 3. 保存端点
        for (CodeEndpoint endpoint : endpoints) {
            System.out.println("发现端点: " + endpoint.getPath());
        }
    }
}
```

## 测试用例

位于 `/test-data/endpoint-examples/`:

1. **UserController.java**
   - 测试 Spring MVC 入口端点
   - 各种 HTTP 方法（GET, POST, PUT, DELETE）

2. **OrderService.java**
   - 测试 RestTemplate 出口端点
   - 各种变量追踪场景

## 扩展点

### 1. 添加自定义规则

在 `code-graph-engine/src/main/resources/endpoint-rules/custom/` 下创建 `.epr` 文件：

```yaml
name: "My Custom Rule"
scope:
  packageIncludes:
    - "com.mycompany.**"
locate:
  nodeType: "MethodInvocation"
  where:
    - receiver:
        typeMatches: ".*MyHttpClient$"
extract:
  # ... 自定义提取逻辑
build:
  endpointType: "HTTP"
  direction: "outbound"
```

### 2. 扩展值追踪器

继承 `UniversalValueTracer` 并覆盖特定方法：

```java
public class CustomValueTracer extends UniversalValueTracer {
    
    @Override
    protected TraceResult traceMethodCall(...) {
        // 自定义方法调用追踪逻辑
    }
}
```

## 已知限制

### ❌ 暂不支持

1. **上下文追踪**（Router 嵌套）
   - 规则已定义，但 ContextManager 尚未实现
   - 需要在未来版本中补充

2. **复杂的控制流**
   - if/else 分支
   - 循环
   - try/catch

3. **跨方法追踪**
   - 方法调用返回值追踪（有限支持）
   - 跨类的字段引用

4. **运行时值**
   - 数据库查询结果
   - 配置文件注入（@Value）
   - 外部 API 返回值

### ⚠️ 部分支持

1. **字符串拼接**: ✅ 支持多层嵌套
2. **变量追踪**: ✅ 局部变量和字段
3. **方法调用**: ⚠️ 标记为 `{METHOD:xxx()}`

## 性能优化

1. **作用域过滤**: 避免对每个文件应用所有规则
2. **规则优先级**: 高优先级规则先执行
3. **追踪深度限制**: 防止无限递归（默认 10 层）
4. **循环引用检测**: 避免死循环

## TODO

- [ ] 实现 ContextManager（支持 Router 嵌套）
- [ ] 支持 Feign Client 解析
- [ ] 支持 Kafka Producer/Consumer
- [ ] 支持 Redis 操作解析
- [ ] 支持配置文件补充机制
- [ ] 添加更多测试用例
- [ ] 性能测试和优化

## 版本历史

### v0.1.0 (当前)
- ✅ 核心框架搭建
- ✅ EPR 规则加载
- ✅ 作用域过滤
- ✅ 通用值追踪
- ✅ Spring MVC 入口支持
- ✅ RestTemplate 出口支持




