package com.poseidon.codegraph.engine.domain.parser.filter;

import com.poseidon.codegraph.engine.domain.model.CodeRelationship;
import org.eclipse.jdt.core.dom.IMethodBinding;

/**
 * Getter/Setter 过滤器
 * 过滤掉简单的属性访问方法（get*, set*, is*）
 */
public class GetterSetterFilter implements RelationshipFilter {

    @Override
    public boolean shouldKeep(CodeRelationship relationship, IMethodBinding targetBinding) {
        String methodName = targetBinding.getName();
        
        // 过滤 Setter
        if (methodName.startsWith("set") && methodName.length() > 3) {
            return false;
        }
        
        // 过滤 Getter
        if ((methodName.startsWith("get") && methodName.length() > 3) ||
            (methodName.startsWith("is") && methodName.length() > 2)) {
            return false;
        }
        
        return true;
    }
}

