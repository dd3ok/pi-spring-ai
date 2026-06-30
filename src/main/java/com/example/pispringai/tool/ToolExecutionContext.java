package com.example.pispringai.tool;

import com.example.pispringai.agent.AgentRunOptions;
import java.util.Objects;

public record ToolExecutionContext(AgentRunOptions options) {

    public ToolExecutionContext {
        Objects.requireNonNull(options, "options must not be null");
    }

    public static ToolExecutionContext safeDefaults() {
        return new ToolExecutionContext(AgentRunOptions.safeDefaults());
    }
}
