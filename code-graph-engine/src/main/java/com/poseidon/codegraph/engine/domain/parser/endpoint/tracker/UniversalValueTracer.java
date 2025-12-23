package com.poseidon.codegraph.engine.domain.parser.endpoint.tracker;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;

/**
 * 通用值追踪器
 * 自动追踪 AST 表达式的值
 */
@Slf4j
public class UniversalValueTracer {
    
    private static final int MAX_DEPTH = 10;  // 防止无限递归
    
    /**
     * 追踪表达式的值
     */
    public TraceResult trace(Expression expr, TraceContext context) {
        return trace(expr, context, 0, new HashSet<>());
    }
    
    private TraceResult trace(Expression expr, TraceContext context, int depth, Set<Expression> visited) {
        // 防止无限递归
        if (depth > MAX_DEPTH) {
            log.warn("追踪深度超过限制: {}", MAX_DEPTH);
            return TraceResult.unknown("{MAX_DEPTH}");
        }
        
        // 防止循环引用
        if (visited.contains(expr)) {
            return TraceResult.unknown("{CIRCULAR}");
        }
        visited.add(expr);
        
        // ===== 类型 1: 字面量（终止条件） =====
        if (expr instanceof StringLiteral) {
            String value = ((StringLiteral) expr).getLiteralValue();
            return TraceResult.full(value);
        }
        
        if (expr instanceof NumberLiteral) {
            String value = ((NumberLiteral) expr).getToken();
            return TraceResult.full(value);
        }
        
        // ===== 类型 2: 变量引用（需要追踪） =====
        if (expr instanceof SimpleName) {
            return traceVariable((SimpleName) expr, context, depth + 1, visited);
        }
        
        // ===== 类型 3: 字段访问（this.field 或 obj.field） =====
        if (expr instanceof FieldAccess) {
            return traceFieldAccess((FieldAccess) expr, context, depth + 1, visited);
        }
        
        // ===== 类型 4: 字符串拼接（递归处理） =====
        if (expr instanceof InfixExpression) {
            InfixExpression infix = (InfixExpression) expr;
            if (infix.getOperator() == InfixExpression.Operator.PLUS) {
                return traceConcat(infix, context, depth + 1, visited);
            }
        }
        
        // ===== 类型 5: 方法调用 =====
        if (expr instanceof MethodInvocation) {
            return TraceResult.unknown("{METHOD:" + ((MethodInvocation) expr).getName() + "()}");
        }
        
        // ===== 类型 6: 括号表达式 =====
        if (expr instanceof ParenthesizedExpression) {
            Expression inner = ((ParenthesizedExpression) expr).getExpression();
            return trace(inner, context, depth, visited);  // 深度不增加
        }
        
        // ===== 其他：未知 =====
        return TraceResult.unknown("{UNKNOWN}");
    }
    
    /**
     * 追踪变量
     */
    private TraceResult traceVariable(SimpleName varName, TraceContext context, int depth, Set<Expression> visited) {
        String name = varName.getIdentifier();
        
        // 1. 在当前方法中查找局部变量定义
        VariableDeclarationFragment localVar = findLocalVariableDeclaration(varName, context.getMethod());
        if (localVar != null) {
            Expression initializer = localVar.getInitializer();
            if (initializer != null) {
                // 递归追踪初始化值
                return trace(initializer, context, depth, visited);
            }
        }

        // 1.1 在当前方法中查找参数定义
        SingleVariableDeclaration param = findParameterDeclaration(varName, context.getMethod());
        if (param != null) {
            // 检查参数上的 @Value 注解
            TraceResult configResult = findValueFromAnnotation(param.modifiers(), context);
            if (configResult != null) {
                return configResult;
            }
        }
        
        // 2. 在类中查找字段
        FieldDeclaration field = findFieldInClass(name, context.getTypeDeclaration());
        if (field != null) {
            // 2.1 检查 @Value 注解
            TraceResult configResult = findValueFromAnnotation(field.modifiers(), context);
            if (configResult != null) {
                return configResult;
            }

            // 2.2 查找字段初始化值
            for (Object fragment : field.fragments()) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                if (vdf.getName().getIdentifier().equals(name)) {
                    Expression initializer = vdf.getInitializer();
                    if (initializer != null) {
                        // 递归追踪字段初始化值
                        return trace(initializer, context, depth, visited);
                    }
                }
            }

            // 2.3 特殊处理：如果字段没有初始化值，尝试在构造函数中查找赋值逻辑
            TraceResult constructorResult = findValueFromConstructor(name, context);
            if (constructorResult != null) {
                return constructorResult;
            }
        }
        
        // 3. 找不到定义，返回占位符
        return TraceResult.partial("{" + name + "}");
    }

    /**
     * 在构造函数中查找字段的赋值逻辑
     */
    private TraceResult findValueFromConstructor(String fieldName, TraceContext context) {
        TypeDeclaration typeDecl = context.getTypeDeclaration();
        if (typeDecl == null) return null;

        for (MethodDeclaration method : typeDecl.getMethods()) {
            if (method.isConstructor()) {
                // 1. 查找是否直接赋值了带有 @Value 的参数
                // 例如: this.baseUrl = baseUrl; 且参数 baseUrl 有 @Value
                for (Object stmt : method.getBody().statements()) {
                    if (stmt instanceof ExpressionStatement) {
                        Expression expr = ((ExpressionStatement) stmt).getExpression();
                        if (expr instanceof Assignment) {
                            Assignment assign = (Assignment) expr;
                            Expression left = assign.getLeftHandSide();
                            Expression right = assign.getRightHandSide();

                            String targetName = null;
                            if (left instanceof SimpleName && ((SimpleName) left).getIdentifier().equals(fieldName)) {
                                targetName = fieldName;
                            } else if (left instanceof FieldAccess && ((FieldAccess) left).getName().getIdentifier().equals(fieldName)) {
                                targetName = fieldName;
                            }

                            if (targetName != null && right instanceof SimpleName) {
                                String paramName = ((SimpleName) right).getIdentifier();
                                for (Object paramObj : method.parameters()) {
                                    SingleVariableDeclaration param = (SingleVariableDeclaration) paramObj;
                                    if (param.getName().getIdentifier().equals(paramName)) {
                                        TraceResult res = findValueFromAnnotation(param.modifiers(), context);
                                        if (res != null) return res;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 从注解中寻找 @Value 的值
     */
    private TraceResult findValueFromAnnotation(List<?> modifiers, TraceContext context) {
        for (Object modifier : modifiers) {
            if (modifier instanceof Annotation) {
                Annotation ann = (Annotation) modifier;
                if ("Value".equals(ann.getTypeName().toString())) {
                    String rawValue = null;
                    if (ann instanceof SingleMemberAnnotation) {
                        Expression value = ((SingleMemberAnnotation) ann).getValue();
                        if (value instanceof StringLiteral) {
                            rawValue = ((StringLiteral) value).getLiteralValue();
                        }
                    } else if (ann instanceof NormalAnnotation) {
                        for (Object pair : ((NormalAnnotation) ann).values()) {
                            MemberValuePair mvp = (MemberValuePair) pair;
                            if ("value".equals(mvp.getName().toString())) {
                                Expression valExpr = mvp.getValue();
                                if (valExpr instanceof StringLiteral) {
                                    rawValue = ((StringLiteral) valExpr).getLiteralValue();
                                }
                            }
                        }
                    }

                    if (rawValue != null) {
                        // 如果有项目根路径，尝试从全局字典解析
                        if (context.getProjectRoot() != null) {
                            ConfigDictionary dict = ConfigRegistry.getDictionary(context.getProjectRoot());
                            List<String> resolvedValues = dict.resolveAll(rawValue);
                            log.debug("全局字典解析 @Value: {} -> {}", rawValue, resolvedValues);
                            return TraceResult.full(resolvedValues);
                        }
                        return TraceResult.full(rawValue);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 在方法参数中查找变量
     */
    private SingleVariableDeclaration findParameterDeclaration(SimpleName varName, MethodDeclaration method) {
        if (method == null) {
            return null;
        }
        String targetName = varName.getIdentifier();
        for (Object param : method.parameters()) {
            SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
            if (svd.getName().getIdentifier().equals(targetName)) {
                return svd;
            }
        }
        return null;
    }
    
    @SuppressWarnings("unchecked")
    private TraceResult traceConcat(InfixExpression expr, TraceContext context, int depth, Set<Expression> visited) {
        List<List<String>> allPartsPossibleValues = new ArrayList<>();
        List<ParseLevel> levels = new ArrayList<>();
        
        // 收集所有操作数
        List<Expression> operands = new ArrayList<>();
        operands.add(expr.getLeftOperand());
        operands.add(expr.getRightOperand());
        if (expr.hasExtendedOperands()) {
            operands.addAll((List<Expression>) expr.extendedOperands());
        }
        
        // 递归追踪每个操作数
        for (Expression operand : operands) {
            TraceResult result = trace(operand, context, depth, visited);
            allPartsPossibleValues.add(result.getPossibleValues());
            levels.add(result.getLevel());
        }
        
        // 笛卡尔积组合所有部分
        List<String> concatenatedResults = cartesianProduct(allPartsPossibleValues);
        
        // 确定整体解析等级
        ParseLevel overallLevel = determineOverallLevel(levels);
        
        return new TraceResult(concatenatedResults, overallLevel);
    }

    /**
     * 计算多个列表的笛卡尔积字符串拼接
     */
    private List<String> cartesianProduct(List<List<String>> lists) {
        List<String> result = new ArrayList<>();
        result.add("");
        for (List<String> list : lists) {
            List<String> nextBatch = new ArrayList<>();
            for (String prefix : result) {
                for (String suffix : list) {
                    nextBatch.add(prefix + suffix);
                }
            }
            result = nextBatch;
        }
        return result;
    }
    
    /**
     * 追踪字段访问
     */
    private TraceResult traceFieldAccess(FieldAccess expr, TraceContext context, int depth, Set<Expression> visited) {
        String fieldName = expr.getName().getIdentifier();
        Expression receiver = expr.getExpression();
        
        // 如果是 this.field
        if (receiver instanceof ThisExpression) {
            FieldDeclaration field = findFieldInClass(fieldName, context.getTypeDeclaration());
            if (field != null) {
                Expression initializer = getFieldInitializer(field, fieldName);
                if (initializer != null) {
                    return trace(initializer, context, depth, visited);
                }
            }
        }
        
        // 其他情况
        return TraceResult.partial("{FIELD:" + fieldName + "}");
    }
    
    /**
     * 在当前方法内查找局部变量
     * 修复：遍历整个方法体，而不是只向上遍历
     */
    private VariableDeclarationFragment findLocalVariableDeclaration(SimpleName varName, MethodDeclaration method) {
        if (method == null || method.getBody() == null) {
            return null;
        }
        
        String targetName = varName.getIdentifier();
        final VariableDeclarationFragment[] result = {null};
        
        // 遍历整个方法体，查找变量声明
        Block methodBody = method.getBody();
        methodBody.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                for (Object fragment : node.fragments()) {
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                    if (vdf.getName().getIdentifier().equals(targetName)) {
                        // 检查变量声明是否在使用之前（通过行号判断）
                        CompilationUnit cu = (CompilationUnit) vdf.getRoot();
                        int declLine = cu.getLineNumber(vdf.getStartPosition());
                        int useLine = cu.getLineNumber(varName.getStartPosition());
                        if (declLine <= useLine) {
                            result[0] = vdf;
                            return false;  // 找到后停止遍历
                        }
                    }
                }
                return true;  // 继续遍历
            }
        });
        
        return result[0];
    }
    
    /**
     * 在类中查找字段
     */
    private FieldDeclaration findFieldInClass(String fieldName, TypeDeclaration typeDecl) {
        if (typeDecl == null) {
            return null;
        }
        
        for (FieldDeclaration field : typeDecl.getFields()) {
            for (Object fragment : field.fragments()) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
                if (vdf.getName().getIdentifier().equals(fieldName)) {
                    return field;
                }
            }
        }
        
        return null;
    }
    
    /**
     * 获取字段的初始化值
     */
    private Expression getFieldInitializer(FieldDeclaration field, String fieldName) {
        for (Object fragment : field.fragments()) {
            VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
            if (vdf.getName().getIdentifier().equals(fieldName)) {
                return vdf.getInitializer();
            }
        }
        return null;
    }
    
    /**
     * 确定整体解析等级
     */
    private ParseLevel determineOverallLevel(List<ParseLevel> levels) {
        if (levels.contains(ParseLevel.UNKNOWN)) {
            return ParseLevel.UNKNOWN;
        }
        if (levels.contains(ParseLevel.PARTIAL)) {
            return ParseLevel.PARTIAL;
        }
        return ParseLevel.FULL;
    }
    
    /**
     * 追踪上下文
     */
    @Data
    public static class TraceContext {
        private final CompilationUnit compilationUnit;
        private final TypeDeclaration typeDeclaration;
        private final MethodDeclaration method;
        private String projectRoot;

        public TraceContext(CompilationUnit compilationUnit, TypeDeclaration typeDeclaration, MethodDeclaration method) {
            this.compilationUnit = compilationUnit;
            this.typeDeclaration = typeDeclaration;
            this.method = method;
        }

        public TraceContext(CompilationUnit compilationUnit, TypeDeclaration typeDeclaration, MethodDeclaration method, String projectRoot) {
            this.compilationUnit = compilationUnit;
            this.typeDeclaration = typeDeclaration;
            this.method = method;
            this.projectRoot = projectRoot;
        }
    }
    
    /**
     * 追踪结果
     */
    @Data
    public static class TraceResult {
        private final String value;
        private final List<String> possibleValues; // 支持多个可能值
        private final ParseLevel level;
        
        public TraceResult(String value, ParseLevel level) {
            this.value = value;
            this.possibleValues = Collections.singletonList(value);
            this.level = level;
        }

        public TraceResult(List<String> possibleValues, ParseLevel level) {
            this.value = possibleValues.isEmpty() ? "" : possibleValues.get(0);
            this.possibleValues = possibleValues;
            this.level = level;
        }
        
        public static TraceResult full(String value) {
            return new TraceResult(value, ParseLevel.FULL);
        }

        public static TraceResult full(List<String> values) {
            return new TraceResult(values, ParseLevel.FULL);
        }
        
        public static TraceResult partial(String value) {
            return new TraceResult(value, ParseLevel.PARTIAL);
        }
        
        public static TraceResult unknown(String value) {
            return new TraceResult(value, ParseLevel.UNKNOWN);
        }
    }
    
    /**
     * 解析等级
     */
    public enum ParseLevel {
        FULL,      // 完全静态
        PARTIAL,   // 部分动态（有占位符）
        UNKNOWN    // 完全动态
    }
}

