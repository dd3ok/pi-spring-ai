package com.example.pispringai.mcp;

import com.example.pispringai.agent.AgentPromptCommand;
import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.agent.AgentRuntime;
import com.example.pispringai.agent.DefaultAgentRuntime;
import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.agent.event.AgentEventType;
import com.example.pispringai.agent.message.ToolResultMessage;
import com.example.pispringai.model.ModelClient;
import com.example.pispringai.model.SpringAiToolCallbackEvents;
import com.example.pispringai.model.SpringAiToolCallbackFactory;
import com.example.pispringai.session.AgentSession;
import com.example.pispringai.session.JsonlSessionStore;
import com.example.pispringai.session.SessionProperties;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolRegistry;
import com.example.pispringai.tool.policy.DefaultToolPolicy;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpRuntimeIntegrationTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @TempDir
    Path tempDir;

    @Test
    void recordsEnabledMcpToolCallInExistingEventAndSessionFlow() {
        AtomicReference<String> delegatedInput = new AtomicReference<>();
        McpAgentTool mcpTool = new McpAgentTool(callback(delegatedInput), objectMapper);
        McpToolBridge bridge = mock(McpToolBridge.class);
        when(bridge.tools()).thenReturn(List.of(mcpTool));

        ToolExecutionService executionService = new ToolExecutionService(
                new DefaultToolPolicy(new ToolProperties(20_000, 30)));
        SpringAiToolCallbackFactory callbackFactory = new SpringAiToolCallbackFactory(
                new ToolRegistry(List.of()),
                executionService,
                objectMapper,
                null,
                bridge);
        ModelClient modelClient = (command, events) -> invokeMcpTool(callbackFactory, command, events);

        JsonlSessionStore store = new JsonlSessionStore(
                new SessionProperties(tempDir.resolve("sessions")),
                objectMapper);
        AgentSession session = store.create(tempDir.resolve("workspace"));
        AgentRuntime runtime = new DefaultAgentRuntime(store, modelClient);
        AgentRunOptions allowMcp = new AgentRunOptions(
                false,
                false,
                true,
                6,
                12,
                Duration.ofSeconds(120));

        List<AgentEvent> events = runtime.prompt(new AgentPromptCommand(
                        session.id(),
                        "echo through MCP",
                        session.workspaceRoot(),
                        allowMcp))
                .collectList()
                .block();

        assertThat(events)
                .extracting(AgentEvent::type)
                .containsExactly(
                        AgentEventType.AGENT_STARTED,
                        AgentEventType.USER_MESSAGE_APPENDED,
                        AgentEventType.TOOL_CALL_STARTED,
                        AgentEventType.TOOL_CALL_FINISHED,
                        AgentEventType.ASSISTANT_DELTA,
                        AgentEventType.ASSISTANT_COMPLETED,
                        AgentEventType.AGENT_COMPLETED);
        ToolResultMessage toolResult = store.loadMessages(session.id()).stream()
                .filter(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(toolResult.toolName()).isEqualTo("mcp_demo_echo");
        assertThat(toolResult.resultText()).isEqualTo("pong");
        assertThat(toolResult.error()).isFalse();
        assertThat(store.loadEvents(session.id())).containsExactlyElementsOf(events);
        assertThat(delegatedInput.get()).isEqualTo("{\"message\":\"hello\"}");
    }

    private static Flux<String> invokeMcpTool(
            SpringAiToolCallbackFactory callbackFactory,
            AgentPromptCommand command,
            SpringAiToolCallbackEvents events
    ) {
        return Flux.defer(() -> {
            ToolCallback callback = callbackFactory
                    .providerFor(command.options(), command.workspaceRoot(), events)
                    .getToolCallbacks()[0];
            callback.call("{\"message\":\"hello\"}");
            return Flux.just("done");
        });
    }

    private static ToolCallback callback(AtomicReference<String> delegatedInput) {
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder()
                        .name("mcp_demo_echo")
                        .description("Echo through a fake MCP server")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\"}},\"required\":[\"message\"]}")
                        .build();
            }

            @Override
            public String call(String toolInput) {
                delegatedInput.set(toolInput);
                return "pong";
            }
        };
    }
}
