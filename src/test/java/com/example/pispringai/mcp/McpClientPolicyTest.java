package com.example.pispringai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.McpConnectionInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpClientPolicyTest {

    @Test
    void allowsOnlyExplicitServerAndToolPair() {
        AllowedMcpToolFilter filter = new AllowedMcpToolFilter(new McpProperties(java.util.Set.of("demo/echo")));
        McpSchema.Tool echo = tool("echo");

        assertThat(filter.test(connection("demo"), echo)).isTrue();
        assertThat(filter.test(connection("other"), echo)).isFalse();
        assertThat(filter.test(connection("demo"), tool("read"))).isFalse();
        assertThat(filter.test(uninitializedConnection(), echo)).isFalse();
    }

    @Test
    void prefixesMcpToolsAndKeepsNamesWithinModelLimit() {
        PiMcpToolNamePrefixGenerator generator = new PiMcpToolNamePrefixGenerator();

        assertThat(generator.prefixedToolName(connection("demo-server"), tool("echo-tool")))
                .isEqualTo("mcp_demo_server_echo_tool");

        String longName = generator.prefixedToolName(
                connection("server-" + "s".repeat(50)),
                tool("tool-" + "t".repeat(50)));
        assertThat(longName).startsWith("mcp_").hasSize(64);
    }

    private static McpConnectionInfo connection(String serverName) {
        McpConnectionInfo connectionInfo = mock(McpConnectionInfo.class);
        McpSchema.InitializeResult initializeResult = mock(McpSchema.InitializeResult.class);
        when(initializeResult.serverInfo()).thenReturn(
                McpSchema.Implementation.builder(serverName, "1.0.0").build());
        when(connectionInfo.initializeResult()).thenReturn(initializeResult);
        return connectionInfo;
    }

    private static McpConnectionInfo uninitializedConnection() {
        return mock(McpConnectionInfo.class);
    }

    private static McpSchema.Tool tool(String name) {
        return McpSchema.Tool.builder(name, Map.of("type", "object", "properties", Map.of()))
                .description("test tool")
                .build();
    }
}
