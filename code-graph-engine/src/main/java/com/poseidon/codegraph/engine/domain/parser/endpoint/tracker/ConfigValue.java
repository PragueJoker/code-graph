package com.poseidon.codegraph.engine.domain.parser.endpoint.tracker;

import lombok.Data;
import java.util.Objects;

/**
 * 配置值对象
 * 记录值及其来源，用于多环境并存和溯源
 */
@Data
public class ConfigValue {
    private final String value;       // 实际物理值
    private final String sourceFile;  // 来源文件（相对路径，如 src/main/resources/application-dev.yml）
    private final String profile;     // 环境标识（如 dev, prod, default）
    private final int priority;       // 优先级（用于排序，profile > default）

    public ConfigValue(String value, String sourceFile, String profile, int priority) {
        this.value = value;
        this.sourceFile = sourceFile;
        this.profile = profile;
        this.priority = priority;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigValue that = (ConfigValue) o;
        return Objects.equals(value, that.value) && Objects.equals(profile, that.profile);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, profile);
    }
}

