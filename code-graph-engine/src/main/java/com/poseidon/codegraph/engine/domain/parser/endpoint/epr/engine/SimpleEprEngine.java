package com.poseidon.codegraph.engine.domain.parser.endpoint.epr.engine;

import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import com.poseidon.codegraph.engine.domain.parser.endpoint.epr.model.*;
import com.poseidon.codegraph.engine.domain.parser.endpoint.tracker.UniversalValueTracer;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 简化版 EPR 执行引擎
 * 执行 EPR 规则，从 AST 中提取端点信息
 */
@Slf4j
public class SimpleEprEngine {
    
    private final UniversalValueTracer valueTracer = new UniversalValueTracer();
    
    /**
     * 执行规则，解析端点
     */
    public List<CodeEndpoint> executeRule(
            EndpointParseRule rule,
            CompilationUnit cu,
            TypeDeclaration typeDecl,
            String projectFilePath) {
        
        List<CodeEndpoint> endpoints = new ArrayList<>();
        
        LocateConfig locate = rule.getLocate();
        if (locate == null) {
            return endpoints;
        }
        
        // 根据节点类型定位
        if ("MethodDeclaration".equals(locate.getNodeType())) {
            // 遍历所有方法
            MethodDeclaration[] methods = typeDecl.getMethods();
            log.debug("规则 {} 检查 {} 个方法", rule.getName(), methods.length);
            
            for (MethodDeclaration method : methods) {
                String methodName = method.getName().getIdentifier();
                log.debug("  检查方法: {}", methodName);
                
                if (matchesConditions(method, locate.getWhere(), typeDecl)) {
                    log.debug("    ✓ 方法 {} 匹配条件", methodName);
                    CodeEndpoint endpoint = extractEndpoint(rule, cu, typeDecl, method, projectFilePath);
                    if (endpoint != null) {
                        endpoints.add(endpoint);
                        log.info("    ✓ 成功提取端点: {} {}", endpoint.getHttpMethod(), endpoint.getPath());
                    }
                } else {
                    log.debug("    ✗ 方法 {} 不匹配条件", methodName);
                }
            }
        } else if ("MethodInvocation".equals(locate.getNodeType())) {
            // 遍历所有方法调用
            for (MethodDeclaration method : typeDecl.getMethods()) {
                if (method.getBody() != null) {
                    method.getBody().accept(new ASTVisitor() {
                        @Override
                        public boolean visit(MethodInvocation invocation) {
                            if (matchesConditions(invocation, locate.getWhere(), typeDecl)) {
                                CodeEndpoint endpoint = extractEndpoint(rule, cu, typeDecl, invocation, projectFilePath);
                                if (endpoint != null) {
                                    endpoints.add(endpoint);
                                }
                            }
                            return true;
                        }
                    });
                }
            }
        }
        
        return endpoints;
    }
    
    /**
     * 检查是否匹配条件
     */
    private boolean matchesConditions(ASTNode node, List<LocateConfig.WhereCondition> conditions, TypeDeclaration typeDecl) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        
        for (LocateConfig.WhereCondition condition : conditions) {
            // 检查注解
            if (condition.getHasAnnotation() != null) {
                if (!hasMatchingAnnotation(node, condition.getHasAnnotation())) {
                    return false;
                }
            }
            
            // 检查方法名
            if (condition.getMethodName() != null && node instanceof MethodInvocation) {
                if (!matchesMethodName((MethodInvocation) node, condition.getMethodName())) {
                    return false;
                }
            }
            
            // 检查接收者
            if (condition.getReceiver() != null && node instanceof MethodInvocation) {
                if (!matchesReceiver((MethodInvocation) node, condition.getReceiver())) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * 检查是否有匹配的注解
     */
    private boolean hasMatchingAnnotation(ASTNode node, LocateConfig.AnnotationCondition condition) {
        List<IExtendedModifier> modifiers = getModifiers(node);
        if (modifiers == null) {
            log.debug("      节点没有修饰符");
            return false;
        }
        
        log.debug("      检查 {} 个修饰符", modifiers.size());
        
        for (IExtendedModifier modifier : modifiers) {
            if (modifier instanceof Annotation) {
                Annotation ann = (Annotation) modifier;
                String annName = ann.getTypeName().toString();
                log.debug("        发现注解: @{}", annName);
                
                // 精确匹配
                if (condition.getNameEquals() != null) {
                    if (annName.equals(condition.getNameEquals().replace("@", ""))) {
                        return true;
                    }
                }
                
                // 正则匹配
                if (condition.getNameMatches() != null) {
                    Pattern pattern = Pattern.compile(condition.getNameMatches().replace("@", ""));
                    if (pattern.matcher(annName).matches()) {
                        return true;
                    }
                }
                
                // 列表匹配
                if (condition.getNameIn() != null) {
                    for (String name : condition.getNameIn()) {
                        if (annName.equals(name.replace("@", ""))) {
                            return true;
                        }
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 获取节点的修饰符列表
     */
    private List<IExtendedModifier> getModifiers(ASTNode node) {
        if (node instanceof MethodDeclaration) {
            return ((MethodDeclaration) node).modifiers();
        }
        if (node instanceof TypeDeclaration) {
            return ((TypeDeclaration) node).modifiers();
        }
        return null;
    }
    
    /**
     * 检查方法名是否匹配
     */
    private boolean matchesMethodName(MethodInvocation invocation, LocateConfig.MethodNameCondition condition) {
        String methodName = invocation.getName().getIdentifier();
        
        if (condition.getIn() != null) {
            return condition.getIn().contains(methodName);
        }
        
        return true;
    }
    
    /**
     * 检查接收者是否匹配
     */
    private boolean matchesReceiver(MethodInvocation invocation, LocateConfig.ReceiverCondition condition) {
        Expression receiver = invocation.getExpression();
        if (receiver == null) {
            return false;
        }
        
        // 检查类型
        if (condition.getTypeMatches() != null) {
            ITypeBinding binding = receiver.resolveTypeBinding();
            if (binding != null) {
                String typeName = binding.getQualifiedName();
                Pattern pattern = Pattern.compile(condition.getTypeMatches());
                return pattern.matcher(typeName).matches();
            }
        }
        
        return true;
    }
    
    /**
     * 从节点中提取端点
     */
    private CodeEndpoint extractEndpoint(
            EndpointParseRule rule,
            CompilationUnit cu,
            TypeDeclaration typeDecl,
            ASTNode node,
            String projectFilePath) {
        
        try {
            // 提取所有字段
            Map<String, String> extractedValues = new HashMap<>();
            
            log.debug("    开始提取字段，共 {} 个字段配置", rule.getExtract().size());
            
            for (Map.Entry<String, ExtractConfig> entry : rule.getExtract().entrySet()) {
                String fieldName = entry.getKey();
                ExtractConfig config = entry.getValue();
                
                log.debug("      提取字段: {}", fieldName);
                String value = extractField(config, node, cu, typeDecl, extractedValues);
                if (value != null) {
                    extractedValues.put(fieldName, value);
                    log.info("      ✓ 提取成功: {} = {}", fieldName, value);
                } else {
                    log.warn("      ✗ 提取失败: {}", fieldName);
                }
            }
            
            log.info("    提取的所有字段: {}", extractedValues);
            
            // 获取有效的 BuildConfig（优先使用显式配置，否则根据 type 生成默认配置）
            BuildConfig buildConfig = DefaultBuildConfigFactory.getEffectiveBuildConfig(
                rule.getType(), 
                rule.getBuild()
            );
            
            if (buildConfig == null) {
                log.warn("规则 {} 没有 build 配置且 type 无效，跳过", rule.getName());
                return null;
            }
            
            // 提取 functionId（如果节点是 MethodDeclaration）
            String functionId = extractFunctionId(node, typeDecl);
            
            // 构建端点
            return buildEndpoint(buildConfig, extractedValues, projectFilePath, functionId);
            
        } catch (Exception e) {
            log.error("提取端点失败: rule={}, error={}", rule.getName(), e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 提取方法的 qualifiedName 作为 functionId
     */
    private String extractFunctionId(ASTNode node, TypeDeclaration typeDecl) {
        if (!(node instanceof org.eclipse.jdt.core.dom.MethodDeclaration)) {
            return null;
        }
        
        org.eclipse.jdt.core.dom.MethodDeclaration method = (org.eclipse.jdt.core.dom.MethodDeclaration) node;
        org.eclipse.jdt.core.dom.IMethodBinding binding = method.resolveBinding();
        
        if (binding == null) {
            log.warn("      方法绑定为空，无法生成 functionId");
            return null;
        }
        
        // 构建 qualifiedName: ClassName.methodName(params):returnType
        StringBuilder qualifiedName = new StringBuilder();
        qualifiedName.append(binding.getDeclaringClass().getQualifiedName());
        qualifiedName.append(".");
        qualifiedName.append(buildMethodSignature(binding));
        
        String functionId = qualifiedName.toString();
        log.info("      ✓ 提取 functionId: {}", functionId);
        return functionId;
    }
    
    /**
     * 构建方法签名
     */
    private String buildMethodSignature(org.eclipse.jdt.core.dom.IMethodBinding binding) {
        StringBuilder sig = new StringBuilder(binding.getName()).append("(");
        org.eclipse.jdt.core.dom.ITypeBinding[] params = binding.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sig.append(", ");
            sig.append(getQualifiedTypeName(params[i]));
        }
        sig.append("):");
        org.eclipse.jdt.core.dom.ITypeBinding returnType = binding.getReturnType();
        if (returnType != null) {
            sig.append(getQualifiedTypeName(returnType));
        } else {
            sig.append("void");
        }
        return sig.toString();
    }
    
    /**
     * 获取类型的限定名
     */
    private String getQualifiedTypeName(org.eclipse.jdt.core.dom.ITypeBinding typeBinding) {
        if (typeBinding.isArray()) {
            org.eclipse.jdt.core.dom.ITypeBinding elementType = typeBinding.getElementType();
            return getQualifiedTypeName(elementType) + "[]";
        } else if (typeBinding.isPrimitive()) {
            return typeBinding.getName();
        } else {
            return typeBinding.getErasure().getQualifiedName();
        }
    }
    
    /**
     * 提取字段值
     */
    private String extractField(
            ExtractConfig config,
            ASTNode node,
            CompilationUnit cu,
            TypeDeclaration typeDecl,
            Map<String, String> extractedValues) {
        
        // 1. 简单提取
        if (config.getFrom() != null) {
            String value = extractFromPath(config.getFrom(), node, cu, typeDecl, config.getTrace());
            // 如果提取失败且有默认值，使用默认值
            if (value == null && config.getDefaultValue() != null) {
                return config.getDefaultValue().toString();
            }
            return value;
        }
        
        // 2. 映射
        if (config.getMapping() != null && node instanceof MethodInvocation) {
            String methodName = ((MethodInvocation) node).getName().getIdentifier();
            Object mappedValue = config.getMapping().get(methodName);
            return mappedValue != null ? mappedValue.toString() : null;
        }
        
        // 3. 组合
        if (config.getCombine() != null) {
            return combineValues(config.getCombine(), extractedValues);
        }
        
        // 4. 策略列表
        if (config.getStrategies() != null) {
            for (ExtractConfig.StrategyConfig strategy : config.getStrategies()) {
                ExtractConfig.TryConfig tryConfig = strategy.getTryConfig();
                if (tryConfig != null) {
                    String value = extractFromTryConfig(tryConfig, node, cu, typeDecl);
                    if (value != null) {
                        return value;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 从路径表达式提取值
     */
    private String extractFromPath(String path, ASTNode node, CompilationUnit cu, TypeDeclaration typeDecl, String traceMode) {
        // 简化实现：支持常见的路径
        
        // argument[0]
        if (path.startsWith("argument[") && node instanceof MethodInvocation) {
            int index = Integer.parseInt(path.substring(9, path.length() - 1));
            MethodInvocation invocation = (MethodInvocation) node;
            List<?> args = invocation.arguments();
            if (index < args.size()) {
                Expression arg = (Expression) args.get(index);
                
                if ("auto".equals(traceMode)) {
                    // 使用追踪器
                    UniversalValueTracer.TraceContext context = new UniversalValueTracer.TraceContext(
                        cu, typeDecl, findEnclosingMethod(node)
                    );
                    UniversalValueTracer.TraceResult result = valueTracer.trace(arg, context);
                    return result.getValue();
                } else {
                    // 直接返回字符串
                    if (arg instanceof StringLiteral) {
                        return ((StringLiteral) arg).getLiteralValue();
                    }
                }
            }
        }
        
        // methodName
        if ("methodName".equals(path)) {
            if (node instanceof MethodInvocation) {
                return ((MethodInvocation) node).getName().getIdentifier();
            }
        }
        
        // method.annotation[pattern].name - 提取注解名称
        if (path.startsWith("method.annotation") && path.endsWith(".name") && node instanceof MethodDeclaration) {
            log.info("        处理 method.annotation.name 路径: {}", path);
            return extractAnnotationName((MethodDeclaration) node, path);
        }
        
        // method.annotation[@XXX].attribute[value]
        if (path.startsWith("method.annotation") && node instanceof MethodDeclaration) {
            log.info("        处理 method.annotation 路径: {}", path);
            return extractAnnotationAttribute((MethodDeclaration) node, path);
        }
        
        // class.annotation[@XXX].attribute[value]
        if (path.startsWith("class.annotation")) {
            log.info("        处理 class.annotation 路径: {}", path);
            return extractAnnotationAttribute(typeDecl, path);
        }
        
        log.warn("        不支持的路径表达式: {}", path);
        return null;
    }
    
    /**
     * 提取注解名称
     * 路径格式: method.annotation[pattern].name
     * 例如: method.annotation[.*Mapping$].name 
     */
    private String extractAnnotationName(MethodDeclaration method, String path) {
        // 解析路径: method.annotation[.*Mapping$].name
        java.util.regex.Pattern pathPattern = java.util.regex.Pattern.compile("annotation\\[([^\\]]+)\\]\\.name");
        java.util.regex.Matcher pathMatcher = pathPattern.matcher(path);
        
        if (!pathMatcher.find()) {
            log.warn("          路径格式错误: {}", path);
            return null;
        }
        
        String annotationPattern = pathMatcher.group(1);
        log.info("          查找匹配注解: pattern={}", annotationPattern);
        
        // 获取方法的所有注解
        List<?> modifiers = method.modifiers();
        for (Object modifier : modifiers) {
            if (modifier instanceof org.eclipse.jdt.core.dom.Annotation) {
                org.eclipse.jdt.core.dom.Annotation annotation = (org.eclipse.jdt.core.dom.Annotation) modifier;
                String annName = annotation.getTypeName().toString();
                
                // 尝试匹配注解名称
                try {
                    java.util.regex.Pattern annPattern = java.util.regex.Pattern.compile(annotationPattern);
                    if (annPattern.matcher(annName).matches()) {
                        log.info("          ✓ 找到匹配注解: {}", annName);
                        return annName;
                    }
                } catch (Exception e) {
                    log.warn("          正则匹配失败: pattern={}, error={}", annotationPattern, e.getMessage());
                }
            }
        }
        
        log.debug("          未找到匹配的注解");
        return null;
    }
    
    /**
     * 提取注解属性
     */
    private String extractAnnotationAttribute(ASTNode node, String path) {
        log.info("          extractAnnotationAttribute: path={}, nodeType={}", path, node.getClass().getSimpleName());
        
        // 解析路径: method.annotation[@GetMapping].attribute[value]
        Pattern annPattern = Pattern.compile("annotation\\[@?([^\\]]+)\\]");
        Pattern attrPattern = Pattern.compile("attribute\\[([^\\]]+)\\]");
        
        Matcher annMatcher = annPattern.matcher(path);
        Matcher attrMatcher = attrPattern.matcher(path);
        
        if (!annMatcher.find() || !attrMatcher.find()) {
            log.warn("          路径解析失败: 无法提取注解名或属性名");
            return null;
        }
        
        String annName = annMatcher.group(1);
        String attrName = attrMatcher.group(1);
        
        log.info("          查找注解: @{}, 属性: {}", annName, attrName);
        
        List<IExtendedModifier> modifiers = getModifiers(node);
        if (modifiers == null) {
            log.warn("          节点没有修饰符");
            return null;
        }
        
        log.info("          检查 {} 个修饰符", modifiers.size());
        
        // 判断是否使用正则匹配
        boolean isRegex = annName.contains("*") || annName.contains("+") || annName.contains("?") 
                       || annName.contains("[") || annName.contains("(") || annName.contains("^") || annName.contains("$");
        
        Pattern annNamePattern = isRegex ? Pattern.compile(annName) : null;
        
        for (IExtendedModifier modifier : modifiers) {
            if (modifier instanceof Annotation) {
                Annotation ann = (Annotation) modifier;
                String actualAnnName = ann.getTypeName().toString();
                log.info("            发现注解: @{}", actualAnnName);
                
                boolean matches = false;
                if (isRegex) {
                    // 正则匹配
                    matches = annNamePattern.matcher(actualAnnName).matches();
                } else {
                    // 精确匹配
                    matches = actualAnnName.equals(annName);
                }
                
                if (matches) {
                    log.info("            ✓ 注解匹配，提取属性: {}", attrName);
                    String value = getAnnotationAttributeValue(ann, attrName);
                    log.info("            属性值: {}", value);
                    return value;
                }
            }
        }
        
        log.warn("          未找到匹配的注解: @{}", annName);
        return null;
    }
    
    /**
     * 获取注解属性值
     */
    private String getAnnotationAttributeValue(Annotation ann, String attrName) {
        if (ann instanceof SingleMemberAnnotation) {
            if ("value".equals(attrName)) {
                Expression value = ((SingleMemberAnnotation) ann).getValue();
                if (value instanceof StringLiteral) {
                    return ((StringLiteral) value).getLiteralValue();
                }
            }
        }
        
        if (ann instanceof NormalAnnotation) {
            for (Object pair : ((NormalAnnotation) ann).values()) {
                MemberValuePair mvp = (MemberValuePair) pair;
                if (mvp.getName().toString().equals(attrName)) {
                    Expression value = mvp.getValue();
                    if (value instanceof StringLiteral) {
                        return ((StringLiteral) value).getLiteralValue();
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 组合多个值
     */
    private String combineValues(ExtractConfig.CombineConfig config, Map<String, String> extractedValues) {
        StringBuilder result = new StringBuilder();
        
        for (ExtractConfig.CombineSource source : config.getSources()) {
            if (source.getSource() != null) {
                // 引用变量: ${basePath}
                String ref = source.getSource();
                if (ref.startsWith("${") && ref.endsWith("}")) {
                    String varName = ref.substring(2, ref.length() - 1);
                    String value = extractedValues.get(varName);
                    if (value != null) {
                        result.append(value);
                    }
                } else {
                    result.append(ref);
                }
            } else if (source.getLiteral() != null) {
                result.append(source.getLiteral());
            }
        }
        
        String combined = result.toString();
        
        // 标准化
        if (Boolean.TRUE.equals(config.getNormalize())) {
            combined = normalizePath(combined);
        }
        
        return combined;
    }
    
    /**
     * 标准化路径
     */
    private String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        
        // 统一多个斜杠
        path = path.replaceAll("/+", "/");
        
        // 确保以斜杠开头
        if (!path.startsWith("/") && !path.isEmpty()) {
            path = "/" + path;
        }
        
        return path;
    }
    
    /**
     * 从 TryConfig 提取值
     */
    private String extractFromTryConfig(ExtractConfig.TryConfig tryConfig, ASTNode node, CompilationUnit cu, TypeDeclaration typeDecl) {
        // 1. 从路径提取值
        String value = null;
        if (tryConfig.getFrom() != null) {
            value = extractFromPath(tryConfig.getFrom(), node, cu, typeDecl, null);
            log.debug("          tryConfig.from={} => value={}", tryConfig.getFrom(), value);
        }
        
        // 2. 如果提取失败，尝试使用 mapping
        if (value == null && tryConfig.getMapping() != null) {
            // mapping 需要一个 key，这里我们可以从 node 获取
            if (node instanceof org.eclipse.jdt.core.dom.MethodInvocation) {
                String methodName = ((org.eclipse.jdt.core.dom.MethodInvocation) node).getName().getIdentifier();
                Object mappedValue = tryConfig.getMapping().get(methodName);
                if (mappedValue != null) {
                    value = mappedValue.toString();
                    log.debug("          mapping[{}] => {}", methodName, value);
                }
            }
        }
        
        // 3. 如果有值，应用 pattern 和 captureGroup
        if (value != null && tryConfig.getPattern() != null) {
            try {
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(tryConfig.getPattern());
                java.util.regex.Matcher matcher = pattern.matcher(value);
                if (matcher.find()) {
                    int group = tryConfig.getCaptureGroup() != null ? tryConfig.getCaptureGroup() : 0;
                    value = matcher.group(group);
                    log.debug("          pattern={}, captureGroup={} => value={}", 
                        tryConfig.getPattern(), group, value);
                } else {
                    log.debug("          pattern={} 不匹配 value={}", tryConfig.getPattern(), value);
                    value = null;
                }
            } catch (Exception e) {
                log.warn("          正则匹配失败: pattern={}, error={}", tryConfig.getPattern(), e.getMessage());
                value = null;
            }
        }
        
        // 4. 应用 transform
        if (value != null && tryConfig.getTransform() != null) {
            String transform = tryConfig.getTransform();
            if ("toUpperCase".equals(transform)) {
                value = value.toUpperCase();
            } else if ("toLowerCase".equals(transform)) {
                value = value.toLowerCase();
            }
            log.debug("          transform={} => value={}", transform, value);
        }
        
        // 5. 如果提取失败，使用默认值
        if (value == null && tryConfig.getDefaultValue() != null) {
            value = tryConfig.getDefaultValue().toString();
            log.debug("          使用默认值: {}", value);
        }
        
        return value;
    }
    
    /**
     * 构建端点
     */
    private CodeEndpoint buildEndpoint(BuildConfig config, Map<String, String> values, String projectFilePath, String functionId) {
        CodeEndpoint endpoint = new CodeEndpoint();
        
        // 基本属性
        endpoint.setEndpointType(resolveValue(config.getEndpointType(), values));
        endpoint.setDirection(resolveValue(config.getDirection(), values));
        endpoint.setIsExternal(config.getIsExternal());
        endpoint.setProjectFilePath(projectFilePath);
        endpoint.setFunctionId(functionId);  // 设置 functionId
        
        // HTTP 属性
        if (config.getHttpMethod() != null) {
            endpoint.setHttpMethod(resolveValue(config.getHttpMethod(), values));
        }
        if (config.getPath() != null) {
            endpoint.setPath(resolveValue(config.getPath(), values));
        }
        
        // Kafka 属性
        if (config.getTopic() != null) {
            endpoint.setTopic(resolveValue(config.getTopic(), values));
        }
        if (config.getOperation() != null) {
            endpoint.setOperation(resolveValue(config.getOperation(), values));
        }
        
        // 生成 ID
        String id = generateEndpointId(endpoint);
        endpoint.setId(id);
        
        return endpoint;
    }
    
    /**
     * 解析值中的变量引用
     */
    private String resolveValue(String template, Map<String, String> values) {
        if (template == null) {
            return null;
        }
        
        // 替换 ${varName}
        Pattern pattern = Pattern.compile("\\$\\{([^}]+)\\}");
        Matcher matcher = pattern.matcher(template);
        
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = values.get(varName);
            matcher.appendReplacement(result, value != null ? value : "");
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * 生成端点 ID
     */
    private String generateEndpointId(CodeEndpoint endpoint) {
        String type = endpoint.getEndpointType();
        String direction = endpoint.getDirection();
        
        if ("HTTP".equals(type)) {
            return String.format("%s:%s:%s:%s",
                direction,
                type,
                endpoint.getHttpMethod(),
                endpoint.getPath()
            );
        } else if ("KAFKA".equals(type)) {
            return String.format("%s:%s:%s",
                direction,
                type,
                endpoint.getTopic()
            );
        }
        
        return type + ":" + direction + ":" + UUID.randomUUID();
    }
    
    /**
     * 查找包含当前节点的方法
     */
    private MethodDeclaration findEnclosingMethod(ASTNode node) {
        ASTNode current = node.getParent();
        while (current != null) {
            if (current instanceof MethodDeclaration) {
                return (MethodDeclaration) current;
            }
            current = current.getParent();
        }
        return null;
    }
}

