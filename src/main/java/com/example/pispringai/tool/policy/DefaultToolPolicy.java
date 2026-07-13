package com.example.pispringai.tool.policy;

import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolOutputLimiter;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class DefaultToolPolicy implements ToolPolicy {

    private final ToolProperties toolProperties;

    public DefaultToolPolicy(ToolProperties toolProperties) {
        this.toolProperties = Objects.requireNonNull(toolProperties, "toolProperties must not be null");
    }

    @Override
    public ToolPolicyDecision before(AgentTool tool, ToolArguments arguments, ToolExecutionContext context) {
        Objects.requireNonNull(tool, "tool must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(context, "context must not be null");

        return switch (tool.spec().safetyLevel()) {
            case READ_ONLY -> ToolPolicyDecision.allow();
            case WRITE -> context.options().allowWrite()
                    ? ToolPolicyDecision.allow()
                    : ToolPolicyDecision.deny("tool requires allowWrite=true: " + tool.spec().name());
            case BASH -> context.options().allowBash()
                    ? ToolPolicyDecision.allow()
                    : ToolPolicyDecision.deny("tool requires allowBash=true: " + tool.spec().name());
            case MCP -> context.options().allowMcp()
                    ? ToolPolicyDecision.allow()
                    : ToolPolicyDecision.deny("tool requires allowMcp=true: " + tool.spec().name());
        };
    }

    @Override
    public ToolResult after(
            AgentTool tool,
            ToolArguments arguments,
            ToolResult result,
            ToolExecutionContext context) {
        Objects.requireNonNull(tool, "tool must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        Objects.requireNonNull(result, "result must not be null");
        Objects.requireNonNull(context, "context must not be null");

        ToolOutputLimiter.LimitedOutput limited = ToolOutputLimiter.limit(
                result.text(),
                toolProperties.maxToolOutputChars());
        if (!limited.truncated()) {
            return result;
        }

        Map<String, Object> metadata = new LinkedHashMap<>(result.metadata());
        metadata.put("truncated", true);
        return new ToolResult(limited.text(), result.error(), metadata);
    }
}
