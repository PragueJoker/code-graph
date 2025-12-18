package com.poseidon.codegraph.engine.domain.parser.processor;

import com.poseidon.codegraph.engine.domain.model.CodeRelationship;
import com.poseidon.codegraph.engine.domain.model.RelationshipType;
import com.poseidon.codegraph.engine.domain.parser.ASTNodeProcessor;
import com.poseidon.codegraph.engine.domain.parser.ProcessorContext;
import com.poseidon.codegraph.engine.domain.parser.filter.FilterPipeline;
import com.poseidon.codegraph.engine.domain.parser.filter.GetterSetterFilter;
import com.poseidon.codegraph.engine.domain.parser.filter.PackageWhitelistFilter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.Set;
import java.util.UUID;

/**
 * 调用关系构建器
 * 
 * 职责：
 * - 从 MethodInvocation 节点提取方法调用关系
 * - 构建 CALLS 关系: Caller Function -> Called Function
 * - 过滤掉不必要的调用（getter/setter、非项目包等）
 * 
 * 优先级：4（可以与节点提取并行，因为不依赖全局节点信息）
 */
@Slf4j
public class CallRelationshipProcessor implements ASTNodeProcessor {
    
    // 司内包前缀白名单（只解析源码和这些包中的方法调用）
    private static final Set<String> INTERNAL_PACKAGE_PREFIXES = Set.of(
        "com.poseidon."
    );
    
    private final FilterPipeline filterPipeline;
    
    public CallRelationshipProcessor() {
        this.filterPipeline = new FilterPipeline()
            .addFilter(new GetterSetterFilter())
            .addFilter(new PackageWhitelistFilter(INTERNAL_PACKAGE_PREFIXES));
    }
    
    @Override
    public void onMethodInvocation(
        MethodInvocation invocation,
        MethodDeclaration enclosingMethod,
        TypeDeclaration enclosingType,
        ProcessorContext context
    ) {
        String methodName = invocation.getName().getIdentifier();
        int lineNumber = context.getCompilationUnit().getLineNumber(invocation.getStartPosition());
        
        // 解析目标方法
        IMethodBinding targetBinding = invocation.resolveMethodBinding();
        if (targetBinding == null) {
            log.debug("方法目标绑定失败（跳过）: file={}, line={}, method={}", 
                context.getProjectFilePath(), lineNumber, methodName);
            return;
        }
        
        // 解析调用者方法
        IMethodBinding callerBinding = enclosingMethod.resolveBinding();
        if (callerBinding == null) {
            log.warn("调用者绑定失败（跳过）: file={}, line={}, callerMethod={}", 
                context.getProjectFilePath(), lineNumber, enclosingMethod.getName().getIdentifier());
            return;
        }
        
        // 构建调用关系
        CodeRelationship rel = new CodeRelationship();
        rel.setId(UUID.randomUUID().toString());
        rel.setRelationshipType(RelationshipType.CALLS);
        
        // From: 调用者
        String callerQualifiedName = buildQualifiedMethodName(callerBinding);
        rel.setFromNodeId(callerQualifiedName);
        
        // To: 被调用者
        String targetQualifiedName = buildQualifiedMethodName(targetBinding);
        rel.setToNodeId(targetQualifiedName);
        
        rel.setLineNumber(lineNumber);
        rel.setCallType("direct");
        rel.setLanguage("java");
        
        // 使用过滤器过滤不必要的关系
        if (!filterPipeline.shouldKeep(rel, targetBinding)) {
            log.trace("✗ 过滤掉调用关系: {} -> {}", callerQualifiedName, targetQualifiedName);
            return;
        }
        
        context.getGraph().addRelationship(rel);
        
        log.trace("✓ 提取调用关系: {} -> {}", callerQualifiedName, targetQualifiedName);
    }
    
    @Override
    public int getPriority() {
        return 4;  // 可以与节点提取并行
    }
    
    @Override
    public String getName() {
        return "CallRelationshipProcessor";
    }
    
    // ========== 辅助方法 ==========
    
    private String buildQualifiedMethodName(IMethodBinding binding) {
        ITypeBinding declaringClass = binding.getDeclaringClass();
        if (declaringClass == null) {
            return binding.getName() + "()";
        }
        
        StringBuilder qualified = new StringBuilder();
        qualified.append(declaringClass.getQualifiedName());
        qualified.append(".");
        qualified.append(binding.getName());
        qualified.append("(");
        
        ITypeBinding[] paramTypes = binding.getParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            if (i > 0) {
                qualified.append(",");
            }
            qualified.append(getQualifiedTypeName(paramTypes[i]));
        }
        
        qualified.append(")");
        return qualified.toString();
    }
    
    private String getQualifiedTypeName(ITypeBinding typeBinding) {
        if (typeBinding == null) {
            return "unknown";
        }
        
        if (typeBinding.isArray()) {
            return getQualifiedTypeName(typeBinding.getElementType()) + "[]";
        }
        
        if (typeBinding.isParameterizedType()) {
            ITypeBinding erasure = typeBinding.getErasure();
            if (erasure != null) {
                return erasure.getQualifiedName();
            }
        }
        
        if (typeBinding.isPrimitive()) {
            return typeBinding.getName();
        }
        
        String qualifiedName = typeBinding.getQualifiedName();
        return qualifiedName != null ? qualifiedName : typeBinding.getName();
    }
}

