package com.poseidon.codegraph.engine.domain.model.endpoint;

import com.poseidon.codegraph.engine.domain.model.CodeEndpoint;
import com.poseidon.codegraph.engine.domain.model.EndpointType;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 数据库端点
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DbEndpoint extends CodeEndpoint {
    private String tableName;
    private String dbOperation; // SELECT, INSERT, UPDATE, DELETE

    public DbEndpoint() {
        setEndpointType(EndpointType.DB);
    }

    @Override
    public String computeMatchIdentity() {
        return "DB:" + (tableName != null ? tableName : "UNKNOWN");
    }
}

