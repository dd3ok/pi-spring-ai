package com.example.pispringai.mcp;

import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolRegistry;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.ToolSpec;
import com.example.pispringai.tool.builtin.BashTool;
import com.example.pispringai.tool.policy.BashCommandPolicy;
import com.example.pispringai.tool.policy.DefaultToolPolicy;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.WorkspaceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class McpServerToolCallbackProviderTest {

    private final ToolExecutionService executionService = mock(ToolExecutionService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ToolRegistry registry = new ToolRegistry(List.of(
            tool("ls", ToolSafetyLevel.READ_ONLY, true),
            tool("read", ToolSafetyLevel.READ_ONLY, true),
            tool("find", ToolSafetyLevel.READ_ONLY, true),
            tool("grep", ToolSafetyLevel.READ_ONLY, true),
            tool("write", ToolSafetyLevel.WRITE, false),
            tool("edit", ToolSafetyLevel.WRITE, false),
            tool("bash", ToolSafetyLevel.BASH, false),
            tool("mcp_remote", ToolSafetyLevel.MCP, false)));

    @Test
    void exposesOnlyReadOnlyToolsByDefault() {
        McpServerToolCallbackProvider provider = provider(
                new McpServerExposureProperties(null, false, false));

        assertThat(callbackNames(provider)).containsExactly("ls", "read", "find", "grep");
    }

    @Test
    void requiresExposureAndPermissionForWriteAndBashTools() {
        assertThatThrownBy(() -> provider(
                new McpServerExposureProperties(List.of("write"), false, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MCP server tool requires explicit permission: write");
        assertThatThrownBy(() -> provider(
                new McpServerExposureProperties(List.of("bash"), true, false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MCP server tool requires explicit permission: bash");

        McpServerToolCallbackProvider provider = provider(
                new McpServerExposureProperties(List.of("write", "edit", "bash"), true, true));

        assertThat(callbackNames(provider)).containsExactly("write", "edit", "bash");
    }

    @Test
    void rejectsUnknownAndDownstreamMcpTools() {
        assertThatThrownBy(() -> provider(
                new McpServerExposureProperties(List.of("missing"), false, false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unknown tool: missing");
        assertThatThrownBy(() -> provider(
                new McpServerExposureProperties(List.of("mcp_remote"), true, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("MCP server tool requires explicit permission: mcp_remote");
    }

    @Test
    void returnsDefensiveCopyOfCallbacks() {
        McpServerToolCallbackProvider provider = provider(
                new McpServerExposureProperties(null, false, false));
        ToolCallback[] first = provider.getToolCallbacks();

        first[0] = null;

        assertThat(provider.getToolCallbacks()[0]).isNotNull();
    }

    @Test
    void keepsDestructiveCommandPolicyWhenBashIsExplicitlyExposed(@TempDir Path workspace) throws Exception {
        ToolProperties toolProperties = new ToolProperties(20_000, 30);
        BashTool bashTool = new BashTool(
                new SafePathResolver(new WorkspaceProperties(workspace, 200_000, 200, 100)),
                new BashCommandPolicy(),
                toolProperties);
        McpServerToolCallbackProvider provider = new McpServerToolCallbackProvider(
                new McpServerExposureProperties(List.of("bash"), false, true),
                new ToolRegistry(List.of(bashTool)),
                new ToolExecutionService(new DefaultToolPolicy(toolProperties)),
                objectMapper);

        String response = provider.getToolCallbacks()[0].call("{\"command\":\"rm -rf .\"}");
        JsonNode result = objectMapper.readTree(response);

        assertThat(result.path("error").asBoolean()).isTrue();
        assertThat(result.path("text").asText())
                .isEqualTo("bash command denied: command is destructive");
    }

    private McpServerToolCallbackProvider provider(McpServerExposureProperties properties) {
        return new McpServerToolCallbackProvider(properties, registry, executionService, objectMapper);
    }

    private static List<String> callbackNames(McpServerToolCallbackProvider provider) {
        return Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition().name())
                .toList();
    }

    private static AgentTool tool(String name, ToolSafetyLevel safetyLevel, boolean enabledByDefault) {
        return new AgentTool() {
            private final ToolSpec spec = new ToolSpec(
                    name,
                    "Test tool " + name,
                    Map.of(),
                    safetyLevel,
                    enabledByDefault);

            @Override
            public ToolSpec spec() {
                return spec;
            }

            @Override
            public ToolResult execute(ToolArguments arguments) {
                return ToolResult.ok(name);
            }
        };
    }
}
