package com.example.pispringai.tool;

import com.example.pispringai.agent.AgentRunOptions;
import java.nio.file.Path;
import java.util.Objects;

public record ToolExecutionContext(AgentRunOptions options, Path workspaceRoot) {

    public ToolExecutionContext(AgentRunOptions options) {
        this(options, null);
    }

    public ToolExecutionContext {
        Objects.requireNonNull(options, "options must not be null");
        workspaceRoot = workspaceRoot == null ? null : workspaceRoot.toAbsolutePath().normalize();
    }

    public static ToolExecutionContext safeDefaults() {
        return new ToolExecutionContext(AgentRunOptions.safeDefaults());
    }
}
