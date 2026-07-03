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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
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
    void promptLinksFollowUpUserMessageToPreviousMessage() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        AgentRuntime runtime = new DefaultAgentRuntime(store, (command, events) -> Flux.just("ok"));

        runtime.prompt(command(session, "first")).collectList().block();
        runtime.prompt(command(session, "second")).collectList().block();

        List<AgentMessage> messages = store.loadMessages(session.id());
        assertThat(messages).hasSize(4);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(3)).isInstanceOf(AssistantMessage.class);
        assertThat(messages.get(2).parentId()).isEqualTo(messages.get(1).id());
        assertThat(messages.get(3).parentId()).isEqualTo(messages.get(2).id());
    }

    @Test
    void promptPassesPersistedSessionMessagesToModelClient() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        AtomicReference<List<AgentMessage>> secondPromptMessages = new AtomicReference<>();
        ModelClient model = (command, events) -> {
            if (command.userText().equals("second")) {
                secondPromptMessages.set(new ArrayList<>(command.messages()));
            }
            return Flux.just("ok");
        };
        AgentRuntime runtime = new DefaultAgentRuntime(store, model);

        runtime.prompt(command(session, "first")).collectList().block();
        runtime.prompt(command(session, "second")).collectList().block();

        assertThat(secondPromptMessages.get())
                .extracting(AgentMessage::kind)
                .containsExactly(
                        com.example.pispringai.agent.message.AgentMessageKind.USER,
                        com.example.pispringai.agent.message.AgentMessageKind.ASSISTANT,
                        com.example.pispringai.agent.message.AgentMessageKind.USER);
        assertThat(((UserMessage) secondPromptMessages.get().get(0)).text()).isEqualTo("first");
        assertThat(((AssistantMessage) secondPromptMessages.get().get(1)).text()).isEqualTo("ok");
        assertThat(((UserMessage) secondPromptMessages.get().get(2)).text()).isEqualTo("second");
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
            events.toolCallStarted("tc_write", "write", new ToolArguments(Map.of("path", "demo.md")));
            events.toolCallDenied("tc_write", "write", ToolResult.error("Denied: allowWrite=false"));
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

    @Test
    void toolCallbacksKeepExplicitCallIdsWhenSameToolFinishesOutOfOrder() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        ModelClient model = (command, events) -> Flux.defer(() -> {
            events.toolCallStarted("tc_first", "read", new ToolArguments(Map.of("path", "one.md")));
            events.toolCallStarted("tc_second", "read", new ToolArguments(Map.of("path", "two.md")));
            events.toolCallFinished("tc_second", "read", ToolResult.ok("second"));
            events.toolCallFinished("tc_first", "read", ToolResult.ok("first"));
            return Flux.just("done");
        });
        AgentRuntime runtime = new DefaultAgentRuntime(store, model);

        List<AgentEvent> events = runtime.prompt(command(session, "read twice")).collectList().block();

        assertThat(events)
                .filteredOn(event -> event.type() == AgentEventType.TOOL_CALL_FINISHED)
                .extracting(event -> ((com.example.pispringai.agent.event.ToolCallFinishedEvent) event).callId())
                .containsExactly("tc_second", "tc_first");
        assertThat(store.loadMessages(session.id()))
                .filteredOn(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .extracting(ToolResultMessage::toolCallId)
                .containsExactly("tc_second", "tc_first");
    }

    @Test
    void concurrentToolResultsAppendWithLinearMessageParents() {
        RecordingSessionStore store = new RecordingSessionStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        ModelClient model = (command, events) -> Flux.defer(() -> {
            events.toolCallStarted("tc_one", "read", new ToolArguments(Map.of("path", "one.md")));
            events.toolCallStarted("tc_two", "read", new ToolArguments(Map.of("path", "two.md")));
            Thread first = new Thread(() -> events.toolCallFinished("tc_one", "read", ToolResult.ok("one")));
            Thread second = new Thread(() -> events.toolCallFinished("tc_two", "read", ToolResult.ok("two")));
            first.start();
            second.start();
            join(first);
            join(second);
            return Flux.just("done");
        });
        AgentRuntime runtime = new DefaultAgentRuntime(store, model);

        runtime.prompt(command(session, "read concurrently")).collectList().block();

        List<ToolResultMessage> toolResults = store.messages().stream()
                .filter(ToolResultMessage.class::isInstance)
                .map(ToolResultMessage.class::cast)
                .toList();
        assertThat(toolResults).hasSize(2);
        assertThat(toolResults.get(1).parentId()).isEqualTo(toolResults.get(0).id());
    }

    @Test
    void failedToolCallbacksPersistErrorToolResultForReplay() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        ModelClient model = (command, events) -> Flux.defer(() -> {
            events.toolCallFailed("tc_bad_args", "read", ToolResult.error("invalid tool arguments"));
            return Flux.just("done");
        });
        AgentRuntime runtime = new DefaultAgentRuntime(store, model);

        List<AgentEvent> events = runtime.prompt(command(session, "bad tool call")).collectList().block();

        assertThat(events)
                .extracting(AgentEvent::type)
                .contains(AgentEventType.AGENT_ERROR);
        ToolResultMessage toolResult = (ToolResultMessage) store.loadMessages(session.id()).get(1);
        assertThat(toolResult.toolCallId()).isEqualTo("tc_bad_args");
        assertThat(toolResult.toolName()).isEqualTo("read");
        assertThat(toolResult.resultText()).isEqualTo("invalid tool arguments");
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

    private static void join(Thread thread) {
        try {
            thread.join(5_000);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for tool callback thread", exception);
        }
        assertThat(thread.isAlive()).isFalse();
    }

    private static final class RecordingSessionStore implements com.example.pispringai.session.SessionStore {

        private final List<AgentMessage> messages = new CopyOnWriteArrayList<>();
        private final List<AgentEvent> events = new CopyOnWriteArrayList<>();
        private AgentSession session;

        @Override
        public AgentSession create(Path workspaceRoot) {
            session = new AgentSession(
                    "s_concurrent",
                    workspaceRoot,
                    java.time.Instant.parse("2026-06-29T00:00:00Z"),
                    java.time.Instant.parse("2026-06-29T00:00:00Z"),
                    null);
            return session;
        }

        @Override
        public java.util.Optional<AgentSession> findSession(String sessionId) {
            return java.util.Optional.ofNullable(session)
                    .filter(candidate -> candidate.id().equals(sessionId));
        }

        @Override
        public void appendMessage(String sessionId, AgentMessage message) {
            messages.add(message);
        }

        @Override
        public void appendEvent(String sessionId, AgentEvent event) {
            events.add(event);
        }

        @Override
        public List<AgentMessage> loadMessages(String sessionId) {
            return List.copyOf(messages);
        }

        @Override
        public List<AgentEvent> loadEvents(String sessionId) {
            return List.copyOf(events);
        }

        private List<AgentMessage> messages() {
            return List.copyOf(messages);
        }
    }
}
