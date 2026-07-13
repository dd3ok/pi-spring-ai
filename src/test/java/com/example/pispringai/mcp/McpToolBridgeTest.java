package com.example.pispringai.mcp;

import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class McpToolBridgeTest {

    @Test
    void returnsNoToolsWhenMcpClientIsDisabled() {
        @SuppressWarnings("unchecked")
        ObjectProvider<SyncMcpToolCallbackProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(new McpToolBridge(provider, new ObjectMapper()).tools()).isEmpty();
    }

    @Test
    void discoversAndExecutesToolThroughRealSpringAiMcpCallbackProvider() {
        McpSyncClient client = mock(McpSyncClient.class);
        McpSchema.Tool remoteTool = McpSchema.Tool.builder(
                        "echo",
                        Map.of("type", "object", "properties", Map.of("message", Map.of("type", "string"))))
                .description("Echo a message")
                .build();
        McpSchema.InitializeResult initializeResult = mock(McpSchema.InitializeResult.class);
        when(initializeResult.serverInfo()).thenReturn(
                McpSchema.Implementation.builder("demo", "1.0.0").build());
        when(client.getCurrentInitializationResult()).thenReturn(initializeResult);
        when(client.getClientCapabilities()).thenReturn(McpSchema.ClientCapabilities.builder().build());
        when(client.getClientInfo()).thenReturn(
                McpSchema.Implementation.builder("pi-spring-ai-test", "1.0.0").build());
        when(client.listTools()).thenReturn(McpSchema.ListToolsResult.builder(List.of(remoteTool)).build());
        when(client.callTool(any())).thenReturn(McpSchema.CallToolResult.builder()
                .addTextContent("pong")
                .isError(false)
                .build());
        SyncMcpToolCallbackProvider mcpProvider = SyncMcpToolCallbackProvider.builder()
                .mcpClients(List.of(client))
                .toolFilter(new AllowedMcpToolFilter(new McpProperties(Set.of("demo/echo"))))
                .toolNamePrefixGenerator(new PiMcpToolNamePrefixGenerator())
                .build();
        @SuppressWarnings("unchecked")
        ObjectProvider<SyncMcpToolCallbackProvider> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(mcpProvider);
        McpToolBridge bridge = new McpToolBridge(provider, new ObjectMapper());

        List<McpAgentTool> tools = bridge.tools();
        ToolResult result = tools.getFirst().execute(new ToolArguments(Map.of("message", "hello")));

        assertThat(tools).extracting(tool -> tool.spec().name()).containsExactly("mcp_demo_echo");
        assertThat(result.text()).contains("pong");
        org.mockito.ArgumentCaptor<McpSchema.CallToolRequest> request =
                org.mockito.ArgumentCaptor.forClass(McpSchema.CallToolRequest.class);
        verify(client).callTool(request.capture());
        assertThat(request.getValue().name()).isEqualTo("echo");
        assertThat(request.getValue().arguments()).containsEntry("message", "hello");
    }
}
