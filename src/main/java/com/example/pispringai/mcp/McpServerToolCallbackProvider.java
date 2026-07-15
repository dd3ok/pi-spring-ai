package com.example.pispringai.mcp;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.model.SpringAiToolCallbackAdapter;
import com.example.pispringai.model.SpringAiToolCallbackEvents;
import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "enabled", havingValue = "true")
public class McpServerToolCallbackProvider implements ToolCallbackProvider {

    private final ToolCallback[] callbacks;

    public McpServerToolCallbackProvider(
            McpServerExposureProperties properties,
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper
    ) {
        AgentRunOptions options = serverOptions(properties);
        Set<String> enabledToolNames = toolRegistry.enabledTools(options).stream()
                .map(tool -> tool.spec().name())
                .collect(Collectors.toUnmodifiableSet());

        this.callbacks = properties.exposedTools().stream()
                .map(toolRegistry::get)
                .map(tool -> requireEnabled(tool, enabledToolNames))
                .map(tool -> new SpringAiToolCallbackAdapter(
                        tool,
                        toolExecutionService,
                        options,
                        objectMapper,
                        SpringAiToolCallbackEvents.noop()))
                .toArray(ToolCallback[]::new);
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return callbacks.clone();
    }

    private static AgentRunOptions serverOptions(McpServerExposureProperties properties) {
        AgentRunOptions defaults = AgentRunOptions.safeDefaults();
        return new AgentRunOptions(
                properties.allowWrite(),
                properties.allowBash(),
                false,
                defaults.maxTurns(),
                defaults.maxToolCalls(),
                defaults.timeout());
    }

    private static AgentTool requireEnabled(AgentTool tool, Set<String> enabledToolNames) {
        if (!enabledToolNames.contains(tool.spec().name())) {
            throw new IllegalStateException(
                    "MCP server tool requires explicit permission: " + tool.spec().name());
        }
        return tool;
    }
}
