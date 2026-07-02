package com.example.pispringai.model;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSpec;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

public class SpringAiToolCallbackAdapter implements ToolCallback {

    private static final TypeReference<Map<String, Object>> ARGUMENTS_TYPE = new TypeReference<>() {
    };

    private final AgentTool tool;
    private final ToolSpec spec;
    private final ToolExecutionService toolExecutionService;
    private final ToolExecutionContext context;
    private final ObjectMapper objectMapper;
    private final SpringAiToolCallbackEvents events;
    private final ToolDefinition toolDefinition;

    public SpringAiToolCallbackAdapter(
            AgentTool tool,
            ToolExecutionService toolExecutionService,
            AgentRunOptions options,
            ObjectMapper objectMapper,
            SpringAiToolCallbackEvents events
    ) {
        this.tool = Objects.requireNonNull(tool, "tool must not be null");
        this.spec = Objects.requireNonNull(tool.spec(), "tool spec must not be null");
        this.toolExecutionService = Objects.requireNonNull(
                toolExecutionService,
                "toolExecutionService must not be null");
        this.context = new ToolExecutionContext(Objects.requireNonNull(options, "options must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.toolDefinition = toToolDefinition(spec);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public String call(String toolInput) {
        ToolArguments arguments;
        try {
            arguments = parseArguments(toolInput);
        } catch (RuntimeException exception) {
            ToolResult result = ToolResult.error("invalid tool arguments: " + exceptionMessage(exception));
            events.toolCallFailed(spec.name(), result);
            return serializeResult(result);
        }

        events.toolCallStarted(spec.name(), arguments);
        ToolResult result = normalizeResult(toolExecutionService.execute(tool, arguments, context));
        if (isPolicyDenied(result)) {
            events.toolCallDenied(spec.name(), result);
        } else {
            events.toolCallFinished(spec.name(), result);
        }
        return serializeResult(result);
    }

    private ToolDefinition toToolDefinition(ToolSpec spec) {
        return ToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(toJsonSchema(spec.inputSchema()))
                .build();
    }

    private String toJsonSchema(Map<String, Object> inputSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        inputSchema.forEach((name, value) -> properties.put(name, toPropertySchema(value)));
        schema.put("properties", properties);
        schema.put("additionalProperties", false);

        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("invalid tool input schema for " + spec.name(), exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toPropertySchema(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            return (Map<String, Object>) mapValue;
        }
        if (value instanceof String type && !type.isBlank()) {
            return Map.of("type", type);
        }
        return Map.of("type", "string");
    }

    private ToolArguments parseArguments(String toolInput) {
        try {
            JsonNode root = objectMapper.readTree(toolInput);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException("expected JSON object");
            }
            return new ToolArguments(objectMapper.convertValue(root, ARGUMENTS_TYPE));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(exceptionMessage(exception), exception);
        }
    }

    private String serializeResult(ToolResult result) {
        result = normalizeResult(result);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            return "{\"text\":\"failed to serialize tool result\",\"error\":true,\"metadata\":{}}";
        }
    }

    private static ToolResult normalizeResult(ToolResult result) {
        return result == null
                ? ToolResult.error("tool execution returned no result")
                : result;
    }

    private static boolean isPolicyDenied(ToolResult result) {
        return result != null && Boolean.TRUE.equals(result.metadata().get("policyDenied"));
    }

    private static String exceptionMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
