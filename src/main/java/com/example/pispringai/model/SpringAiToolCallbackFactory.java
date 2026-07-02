package com.example.pispringai.model;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpringAiToolCallbackFactory {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionService toolExecutionService;
    private final ObjectMapper objectMapper;
    private final SpringAiToolCallbackEvents events;

    @Autowired
    public SpringAiToolCallbackFactory(
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper,
            ObjectProvider<SpringAiToolCallbackEvents> events
    ) {
        this(
                toolRegistry,
                toolExecutionService,
                objectMapper,
                events == null
                        ? SpringAiToolCallbackEvents.noop()
                        : events.getIfAvailable(SpringAiToolCallbackEvents::noop));
    }

    SpringAiToolCallbackFactory(
            ToolRegistry toolRegistry,
            ToolExecutionService toolExecutionService,
            ObjectMapper objectMapper,
            SpringAiToolCallbackEvents events
    ) {
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.toolExecutionService = Objects.requireNonNull(
                toolExecutionService,
                "toolExecutionService must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
    }

    public List<ToolCallback> callbacksFor(AgentRunOptions options) {
        return toolRegistry.enabledTools(options).stream()
                .map(tool -> new SpringAiToolCallbackAdapter(
                        tool,
                        toolExecutionService,
                        options,
                        objectMapper,
                        events))
                .map(ToolCallback.class::cast)
                .toList();
    }

    public ToolCallbackProvider providerFor(AgentRunOptions options) {
        return ToolCallbackProvider.from(callbacksFor(options));
    }
}
