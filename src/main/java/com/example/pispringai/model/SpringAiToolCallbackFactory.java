package com.example.pispringai.model;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.mcp.McpAgentTool;
import com.example.pispringai.mcp.McpToolBridge;
import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpringAiToolCallbackFactory {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;
    private final SpringAiToolCallbackEvents events;
    private final McpToolBridge mcpToolBridge;

    @Autowired
    public SpringAiToolCallbackFactory(
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper,
            ObjectProvider<SpringAiToolCallbackEvents> events,
            McpToolBridge mcpToolBridge
    ) {
        this(
                toolRegistry,
                toolExecutionService,
                objectMapper,
                events == null
                        ? SpringAiToolCallbackEvents.noop()
                        : events.getIfAvailable(SpringAiToolCallbackEvents::noop),
                mcpToolBridge);
    }

    SpringAiToolCallbackFactory(
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper,
            SpringAiToolCallbackEvents events,
            McpToolBridge mcpToolBridge
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.toolExecutionService = Objects.requireNonNull(
                toolExecutionService,
                "toolExecutionService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.mcpToolBridge = Objects.requireNonNull(mcpToolBridge, "mcpToolBridge must not be null");
    }

    public List<ToolCallback> callbacksFor(AgentRunOptions options) {
        return callbacksFor(options, events);
    }

    public List<ToolCallback> callbacksFor(AgentRunOptions options, SpringAiToolCallbackEvents callbackEvents) {
        return callbacksFor(options, null, callbackEvents);
    }

    public List<ToolCallback> callbacksFor(
            AgentRunOptions options,
            Path workspaceRoot,
            SpringAiToolCallbackEvents callbackEvents
    ) {
        SpringAiToolCallbackEvents resolvedEvents = callbackEvents == null ? events : callbackEvents;
        List<McpAgentTool> mcpTools = options.allowMcp()
                ? mcpToolBridge.tools()
                : List.of();
        return toolRegistry.enabledTools(options, mcpTools).stream()
                .map(tool -> new SpringAiToolCallbackAdapter(
                        tool,
                        toolExecutionService,
                        options,
                        workspaceRoot,
                        objectMapper,
                        resolvedEvents,
                        toolDefinition(tool)))
                .map(ToolCallback.class::cast)
                .toList();
    }

    private static ToolDefinition toolDefinition(AgentTool tool) {
        return tool instanceof McpAgentTool mcpTool ? mcpTool.toolDefinition() : null;
    }

    public ToolCallbackProvider providerFor(AgentRunOptions options) {
        return () -> callbacksFor(options).toArray(ToolCallback[]::new);
    }

    public ToolCallbackProvider providerFor(AgentRunOptions options, SpringAiToolCallbackEvents callbackEvents) {
        return () -> callbacksFor(options, callbackEvents).toArray(ToolCallback[]::new);
    }

    public ToolCallbackProvider providerFor(
            AgentRunOptions options,
            Path workspaceRoot,
            SpringAiToolCallbackEvents callbackEvents
    ) {
        return () -> callbacksFor(options, workspaceRoot, callbackEvents).toArray(ToolCallback[]::new);
    }
}
