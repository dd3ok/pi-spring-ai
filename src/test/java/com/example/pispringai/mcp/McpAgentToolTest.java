package com.example.pispringai.mcp;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.policy.DefaultToolPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;

class McpAgentToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void delegatesArgumentsAndKeepsOriginalToolDefinition() {
        AtomicReference<String> input = new AtomicReference<>();
        ToolDefinition definition = definition("mcp_demo_echo");
        McpAgentTool tool = new McpAgentTool(callback(definition, input, new AtomicInteger()), objectMapper);

        ToolResult result = tool.execute(new ToolArguments(Map.of("message", "hello")));

        assertThat(tool.spec().safetyLevel()).isEqualTo(ToolSafetyLevel.MCP);
        assertThat(tool.spec().enabledByDefault()).isFalse();
        assertThat(tool.toolDefinition()).isSameAs(definition);
        assertThat(input.get()).isEqualTo("{\"message\":\"hello\"}");
        assertThat(result.text()).isEqualTo("response");
        assertThat(result.metadata()).containsEntry("source", "mcp");
    }

    @Test
    void executesOnlyAfterAllowMcpAndAppliesCommonOutputLimit() {
        AtomicInteger calls = new AtomicInteger();
        McpAgentTool tool = new McpAgentTool(callback(definition("mcp_demo_echo"), new AtomicReference<>(), calls), objectMapper);
        ToolExecutionService service = new ToolExecutionService(
                new DefaultToolPolicy(new ToolProperties(5, 30)));

        ToolResult denied = service.execute(
                tool,
                new ToolArguments(Map.of()),
                new ToolExecutionContext(AgentRunOptions.safeDefaults()));

        assertThat(denied.error()).isTrue();
        assertThat(denied.text()).contains("allowMcp");
        assertThat(calls).hasValue(0);

        AgentRunOptions allowMcp = new AgentRunOptions(
                false,
                false,
                true,
                6,
                12,
                Duration.ofSeconds(120));
        ToolResult allowed = service.execute(
                tool,
                new ToolArguments(Map.of()),
                new ToolExecutionContext(allowMcp));

        assertThat(allowed.error()).isFalse();
        assertThat(allowed.text()).isEqualTo("respo");
        assertThat(allowed.metadata()).containsEntry("source", "mcp").containsEntry("truncated", true);
        assertThat(calls).hasValue(1);
    }

    private static ToolCallback callback(
            ToolDefinition definition,
            AtomicReference<String> input,
            AtomicInteger calls
    ) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                input.set(toolInput);
                calls.incrementAndGet();
                return "response";
            }
        };
    }

    private static ToolDefinition definition(String name) {
        return ToolDefinition.builder()
                .name(name)
                .description("Echo through an MCP server")
                .inputSchema("{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},\"required\":[\"message\"]}")
                .build();
    }
}
