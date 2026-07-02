package com.example.pispringai.agent;

import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.agent.event.AgentEventType;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.agent.message.AssistantMessage;
import com.example.pispringai.agent.message.AssistantStopReason;
import com.example.pispringai.agent.message.ToolResultMessage;
import com.example.pispringai.agent.message.UserMessage;
import com.example.pispringai.model.ModelClient;
import com.example.pispringai.model.SpringAiToolCallbackEvents;
import com.example.pispringai.session.AgentSession;
import com.example.pispringai.session.JsonlSessionStore;
import com.example.pispringai.session.SessionProperties;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAgentRuntimeTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @TempDir
    Path tempDir;

    @Test
    void promptStreamsEventsAndPersistsUserAndAssistantMessages() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        AgentRuntime runtime = new DefaultAgentRuntime(store, (command, events) -> Flux.just("hel", "lo"));

        List<AgentEvent> events = runtime.prompt(command(session, "say hello")).collectList().block();

        assertThat(events)
                .extracting(AgentEvent::type)
                .containsExactly(
                        AgentEventType.AGENT_STARTED,
                        AgentEventType.USER_MESSAGE_APPENDED,
                        AgentEventType.ASSISTANT_DELTA,
                        AgentEventType.ASSISTANT_DELTA,
                        AgentEventType.ASSISTANT_COMPLETED,
                        AgentEventType.AGENT_COMPLETED);
        assertThat(store.loadEvents(session.id())).containsExactlyElementsOf(events);

        assertThat(store.loadMessages(session.id()))
                .hasSize(2)
                .satisfiesExactly(
                        message -> {
                            assertThat(message).isInstanceOf(UserMessage.class);
                            assertThat(((UserMessage) message).text()).isEqualTo("say hello");
                        },
                        message -> {
                            assertThat(message).isInstanceOf(AssistantMessage.class);
                            AssistantMessage assistant = (AssistantMessage) message;
                            assertThat(assistant.text()).isEqualTo("hello");
                            assertThat(assistant.stopReason()).isEqualTo(AssistantStopReason.STOP);
                        });
    }

    @Test
    void toolCallbacksEmitEventsAndPersistToolResultMessages() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        ModelClient model = (command, events) -> Flux.defer(() -> {
            events.toolCallStarted("read", new ToolArguments(Map.of("path", "README.md")));
            events.toolCallFinished("read", ToolResult.ok("README content"));
            return Flux.just("done");
        });
        AgentRuntime runtime = new DefaultAgentRuntime(store, model);

        List<AgentEvent> events = runtime.prompt(command(session, "read README")).collectList().block();

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

        List<AgentMessage> messages = store.loadMessages(session.id());
        assertThat(messages).hasSize(3);
        assertThat(messages.get(1)).isInstanceOf(ToolResultMessage.class);
        ToolResultMessage toolResult = (ToolResultMessage) messages.get(1);
        assertThat(toolResult.toolName()).isEqualTo("read");
        assertThat(toolResult.resultText()).isEqualTo("README content");
        assertThat(toolResult.error()).isFalse();
        assertThat(messages.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(((AssistantMessage) messages.get(2)).text()).isEqualTo("done");
    }

    @Test
    void deniedToolCallbacksEmitDeniedEventAndPersistErrorToolResult() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        ModelClient model = (command, events) -> Flux.defer(() -> {
            events.toolCallStarted("write", new ToolArguments(Map.of("path", "demo.md")));
            events.toolCallDenied("write", ToolResult.error("Denied: allowWrite=false"));
            return Flux.just("cannot write");
        });
        AgentRuntime runtime = new DefaultAgentRuntime(store, model);

        List<AgentEvent> events = runtime.prompt(command(session, "write demo")).collectList().block();

        assertThat(events)
                .extracting(AgentEvent::type)
                .containsExactly(
                        AgentEventType.AGENT_STARTED,
                        AgentEventType.USER_MESSAGE_APPENDED,
                        AgentEventType.TOOL_CALL_STARTED,
                        AgentEventType.TOOL_CALL_DENIED,
                        AgentEventType.ASSISTANT_DELTA,
                        AgentEventType.ASSISTANT_COMPLETED,
                        AgentEventType.AGENT_COMPLETED);

        ToolResultMessage toolResult = (ToolResultMessage) store.loadMessages(session.id()).get(1);
        assertThat(toolResult.toolName()).isEqualTo("write");
        assertThat(toolResult.resultText()).isEqualTo("Denied: allowWrite=false");
        assertThat(toolResult.error()).isTrue();
    }

    private JsonlSessionStore newStore() {
        return new JsonlSessionStore(new SessionProperties(tempDir.resolve("sessions")), objectMapper);
    }

    private static AgentPromptCommand command(AgentSession session, String text) {
        return new AgentPromptCommand(
                session.id(),
                text,
                session.workspaceRoot(),
                AgentRunOptions.safeDefaults());
    }
}
