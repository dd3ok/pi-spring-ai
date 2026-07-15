package com.example.pispringai.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "AGENT_MODEL_PROVIDER=google-genai",
                "AGENT_MCP_ENABLED=false",
                "AGENT_MCP_SERVER_ENABLED=true",
                "spring.ai.google.genai.api-key=test-gemini-api-key"
        })
class McpServerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void exposesReadOnlyToolsAndKeepsWorkspaceSafetyThroughStreamableHttp() throws Exception {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder("http://127.0.0.1:" + port)
                .endpoint("/mcp")
                .build();

        try (McpSyncClient client = McpClient.sync(transport)
                .clientInfo(McpSchema.Implementation.builder("pi-spring-ai-test", "1.0.0").build())
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            McpSchema.InitializeResult initializeResult = client.initialize();

            assertThat(initializeResult.serverInfo().name()).isEqualTo("pi-spring-ai");
            assertThat(client.listTools().tools())
                    .extracting(McpSchema.Tool::name)
                    .containsExactlyInAnyOrder("ls", "read", "find", "grep");

            JsonNode readResult = callTool(client, "read", Map.of("path", "README.md"));
            assertThat(readResult.path("error").asBoolean()).isFalse();
            assertThat(readResult.path("text").asText()).contains("# pi-spring-ai");

            JsonNode deniedResult = callTool(client, "read", Map.of("path", ".."));
            assertThat(deniedResult.path("error").asBoolean()).isTrue();
            assertThat(deniedResult.path("text").asText()).contains("outside workspace");
        }
    }

    private JsonNode callTool(McpSyncClient client, String name, Map<String, Object> arguments) throws Exception {
        McpSchema.CallToolResult result = client.callTool(McpSchema.CallToolRequest.builder(name)
                .arguments(arguments)
                .build());

        assertThat(result.isError()).isFalse();
        assertThat(result.content()).hasSize(1);
        McpSchema.TextContent content = (McpSchema.TextContent) result.content().getFirst();
        return objectMapper.readTree(content.text());
    }
}
