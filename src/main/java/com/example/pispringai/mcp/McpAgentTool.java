package com.example.pispringai.mcp;

import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.ToolSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public final class McpAgentTool implements AgentTool {

    private final ToolCallback delegate;
    private final ObjectMapper objectMapper;
    private final ToolSpec spec;
    private final ToolDefinition toolDefinition;

    public McpAgentTool(ToolCallback delegate, ObjectMapper objectMapper) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.toolDefinition = Objects.requireNonNull(
                delegate.getToolDefinition(),
                "MCP tool definition must not be null");
        String description = toolDefinition.description() == null || toolDefinition.description().isBlank()
                ? "Tool provided by an MCP server."
                : toolDefinition.description();
        this.spec = new ToolSpec(
                toolDefinition.name(),
                description,
                Map.of(),
                ToolSafetyLevel.MCP,
                false);
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult execute(ToolArguments arguments) {
        String input;
        try {
            input = objectMapper.writeValueAsString(arguments.values());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to serialize MCP tool arguments", exception);
        }

        String output = delegate.call(input);
        if (output == null) {
            throw new IllegalStateException("MCP tool returned no result: " + spec.name());
        }
        return ToolResult.ok(output, Map.of("source", "mcp"));
    }

    public ToolDefinition toolDefinition() {
        return toolDefinition;
    }
}
