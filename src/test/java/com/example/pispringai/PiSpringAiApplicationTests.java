package com.example.pispringai;

import com.example.pispringai.mcp.McpServerToolCallbackProvider;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureWebTestClient
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "AGENT_MODEL_PROVIDER=google-genai",
                "AGENT_MCP_ENABLED=false",
                "spring.ai.google.genai.api-key=test-gemini-api-key"
        })
class PiSpringAiApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void exposesHealthEndpoint() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void keepsMcpClientDisabledWithoutExplicitOptIn() {
        assertThat(applicationContext.getBeansOfType(SyncMcpToolCallbackProvider.class))
                .isEmpty();
    }

    @Test
    void keepsMcpServerDisabledWithoutExplicitOptIn() {
        assertThat(applicationContext.getBeansOfType(McpServerToolCallbackProvider.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(McpSyncServer.class))
                .isEmpty();
    }
}
