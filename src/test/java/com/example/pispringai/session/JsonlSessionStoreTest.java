package com.example.pispringai.session;

import com.example.pispringai.agent.event.AgentCompletedEvent;
import com.example.pispringai.agent.event.AgentErrorEvent;
import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.agent.event.AgentStartedEvent;
import com.example.pispringai.agent.event.AssistantCompletedEvent;
import com.example.pispringai.agent.event.AssistantDeltaEvent;
import com.example.pispringai.agent.event.ToolCallDeniedEvent;
import com.example.pispringai.agent.event.ToolCallFinishedEvent;
import com.example.pispringai.agent.event.ToolCallStartedEvent;
import com.example.pispringai.agent.event.UserMessageAppendedEvent;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.agent.message.AssistantMessage;
import com.example.pispringai.agent.message.AssistantStopReason;
import com.example.pispringai.agent.message.ToolResultMessage;
import com.example.pispringai.agent.message.UserMessage;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonlSessionStoreTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @TempDir
    Path tempDir;

    @Test
    void createStoresMetadataAndFindsSession() throws Exception {
        JsonlSessionStore store = newStore();
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace);

        AgentSession session = store.create(workspace);

        assertThat(session.id()).startsWith("s_");
        assertThat(session.workspaceRoot()).isEqualTo(workspace.toAbsolutePath().normalize());
        assertThat(session.createdAt()).isNotNull();
        assertThat(session.updatedAt()).isEqualTo(session.createdAt());
        assertThat(session.title()).isNull();

        Path sessionDir = sessionDir(session.id());
        assertThat(sessionDir).isDirectory();
        assertThat(sessionDir.resolve("session.json")).isRegularFile();
        assertThat(sessionDir.resolve("messages.jsonl")).isRegularFile();
        assertThat(sessionDir.resolve("events.jsonl")).isRegularFile();

        JsonNode metadata = objectMapper.readTree(sessionDir.resolve("session.json").toFile());
        assertThat(metadata.get("id").asText()).isEqualTo(session.id());
        assertThat(metadata.get("workspaceRoot").asText()).isEqualTo(workspace.toAbsolutePath().normalize().toString());

        assertThat(store.findSession(session.id())).contains(session);
    }

    @Test
    void appendsAndLoadsMessagesAsFlatJsonlInOrder() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        List<AgentMessage> messages = List.of(
                new UserMessage("m_1", session.id(), null, Instant.parse("2026-06-29T00:00:00Z"), "hello"),
                new AssistantMessage(
                        "m_2",
                        session.id(),
                        "m_1",
                        Instant.parse("2026-06-29T00:00:01Z"),
                        "hi",
                        AssistantStopReason.STOP),
                new ToolResultMessage(
                        "m_3",
                        session.id(),
                        "m_2",
                        Instant.parse("2026-06-29T00:00:02Z"),
                        "tc_1",
                        "read",
                        "README.md",
                        false));

        messages.forEach(message -> store.appendMessage(session.id(), message));

        assertThat(store.loadMessages(session.id())).containsExactlyElementsOf(messages);

        List<String> lines = Files.readAllLines(sessionDir(session.id()).resolve("messages.jsonl"));
        assertThat(lines).hasSize(3);
        JsonNode first = objectMapper.readTree(lines.getFirst());
        JsonNode second = objectMapper.readTree(lines.get(1));
        JsonNode third = objectMapper.readTree(lines.get(2));
        assertThat(first.get("kind").asText()).isEqualTo("user");
        assertThat(first.has("text")).isTrue();
        assertThat(second.get("kind").asText()).isEqualTo("assistant");
        assertThat(second.get("stopReason").asText()).isEqualTo("STOP");
        assertThat(third.get("kind").asText()).isEqualTo("tool_result");
        assertThat(third.get("toolName").asText()).isEqualTo("read");
        assertThat(first.has("payload")).isFalse();
        assertThat(first.has("json")).isFalse();
    }

    @Test
    void appendUsesLfLineSeparatorInJsonlFiles() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));

        store.appendMessage(session.id(), sampleUserMessage(session.id()));

        String content = Files.readString(sessionDir(session.id()).resolve("messages.jsonl"), StandardCharsets.UTF_8);
        assertThat(content).endsWith("\n");
        assertThat(content).doesNotContain("\r");
    }

    @Test
    void loadMessagesSkipsBlankJsonlLines() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        UserMessage message = sampleUserMessage(session.id());
        Path messages = sessionDir(session.id()).resolve("messages.jsonl");
        Files.writeString(
                messages,
                "\n" + objectMapper.writeValueAsString(message) + "\n   \n",
                StandardCharsets.UTF_8);

        assertThat(store.loadMessages(session.id())).containsExactly(message);
    }

    @Test
    void appendsAndLoadsAllEventVariantsAsFlatJsonlInOrder() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        List<AgentEvent> events = List.of(
                new AgentStartedEvent("e_1", session.id(), Instant.parse("2026-06-29T00:00:00Z")),
                new UserMessageAppendedEvent("e_2", session.id(), Instant.parse("2026-06-29T00:00:01Z"), "m_1"),
                new AssistantDeltaEvent("e_3", session.id(), Instant.parse("2026-06-29T00:00:02Z"), "hello"),
                new AssistantCompletedEvent("e_4", session.id(), Instant.parse("2026-06-29T00:00:03Z"), "m_2"),
                new ToolCallStartedEvent("e_5", session.id(), Instant.parse("2026-06-29T00:00:04Z"), "tc_1", "read"),
                new ToolCallFinishedEvent(
                        "e_6",
                        session.id(),
                        Instant.parse("2026-06-29T00:00:05Z"),
                        "tc_1",
                        "read",
                        false),
                new ToolCallDeniedEvent(
                        "e_7",
                        session.id(),
                        Instant.parse("2026-06-29T00:00:06Z"),
                        "tc_2",
                        "read",
                        "denied"),
                new AgentErrorEvent(
                        "e_8",
                        session.id(),
                        Instant.parse("2026-06-29T00:00:07Z"),
                        "failed",
                        "MODEL_ERROR",
                        true),
                new AgentCompletedEvent("e_9", session.id(), Instant.parse("2026-06-29T00:00:08Z")));

        events.forEach(event -> store.appendEvent(session.id(), event));

        assertThat(store.loadEvents(session.id())).containsExactlyElementsOf(events);

        List<String> lines = Files.readAllLines(sessionDir(session.id()).resolve("events.jsonl"));
        assertThat(lines).hasSize(9);
        JsonNode first = objectMapper.readTree(lines.getFirst());
        assertThat(first.get("type").asText()).isEqualTo("agent_started");
        assertThat(first.has("payload")).isFalse();
        assertThat(first.has("json")).isFalse();
        assertThat(objectMapper.readTree(lines.get(8)).get("type").asText()).isEqualTo("agent_completed");
    }

    @Test
    void loadEventsSkipsBlankJsonlLines() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        AgentStartedEvent event = sampleStartedEvent(session.id());
        Path events = sessionDir(session.id()).resolve("events.jsonl");
        Files.writeString(
                events,
                "\n" + objectMapper.writeValueAsString(event) + "\n   \n",
                StandardCharsets.UTF_8);

        assertThat(store.loadEvents(session.id())).containsExactly(event);
    }

    @Test
    void rejectsUnknownMissingNonTextAndMalformedMessageDiscriminators() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        Path messages = sessionDir(session.id()).resolve("messages.jsonl");

        writeLine(messages, "{\"kind\":\"alien\",\"id\":\"m_1\",\"sessionId\":\"%s\",\"createdAt\":\"2026-06-29T00:00:00Z\"}"
                .formatted(session.id()));
        assertThatThrownBy(() -> store.loadMessages(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Unknown message kind");

        writeLine(messages, "{\"id\":\"m_1\",\"sessionId\":\"%s\",\"createdAt\":\"2026-06-29T00:00:00Z\"}"
                .formatted(session.id()));
        assertThatThrownBy(() -> store.loadMessages(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Missing text message kind");

        writeLine(messages, "{\"kind\":123,\"id\":\"m_1\",\"sessionId\":\"%s\",\"createdAt\":\"2026-06-29T00:00:00Z\"}"
                .formatted(session.id()));
        assertThatThrownBy(() -> store.loadMessages(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Missing text message kind");

        writeLine(messages, "{\"kind\":\"user\"");
        assertThatThrownBy(() -> store.loadMessages(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Failed to read messages");
    }

    @Test
    void rejectsUnknownMissingNonTextAndMalformedEventDiscriminators() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        Path events = sessionDir(session.id()).resolve("events.jsonl");

        writeLine(events, "{\"type\":\"alien\",\"eventId\":\"e_1\",\"sessionId\":\"%s\",\"createdAt\":\"2026-06-29T00:00:00Z\"}"
                .formatted(session.id()));
        assertThatThrownBy(() -> store.loadEvents(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Unknown event type");

        writeLine(events, "{\"eventId\":\"e_1\",\"sessionId\":\"%s\",\"createdAt\":\"2026-06-29T00:00:00Z\"}"
                .formatted(session.id()));
        assertThatThrownBy(() -> store.loadEvents(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Missing text event type");

        writeLine(events, "{\"type\":123,\"eventId\":\"e_1\",\"sessionId\":\"%s\",\"createdAt\":\"2026-06-29T00:00:00Z\"}"
                .formatted(session.id()));
        assertThatThrownBy(() -> store.loadEvents(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Missing text event type");

        writeLine(events, "{\"type\":\"agent_started\"");
        assertThatThrownBy(() -> store.loadEvents(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Failed to read events");
    }

    @Test
    void missingSessionsAreEmptyForFindAndFailForAppendOrLoad() {
        JsonlSessionStore store = newStore();

        assertThat(store.findSession("s_missing")).isEmpty();
        assertThatThrownBy(() -> store.appendMessage("s_missing", sampleUserMessage("s_missing")))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Session not found");
        assertThatThrownBy(() -> store.appendEvent("s_missing", sampleStartedEvent("s_missing")))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Session not found");
        assertThatThrownBy(() -> store.loadMessages("s_missing"))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Session not found");
        assertThatThrownBy(() -> store.loadEvents("s_missing"))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void appendRejectsRecordsForDifferentSession() {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));

        assertThatThrownBy(() -> store.appendMessage(session.id(), sampleUserMessage("s_other")))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("does not belong to session");
        assertThatThrownBy(() -> store.appendEvent(session.id(), sampleStartedEvent("s_other")))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("does not belong to session");
    }

    @Test
    void appendRejectsNullRecordsBeforeWritingJsonl() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));

        assertThatThrownBy(() -> store.appendMessage(session.id(), null))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("message must not be null");
        assertThatThrownBy(() -> store.appendEvent(session.id(), null))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("event must not be null");

        assertThat(Files.readAllLines(sessionDir(session.id()).resolve("messages.jsonl"))).isEmpty();
        assertThat(Files.readAllLines(sessionDir(session.id()).resolve("events.jsonl"))).isEmpty();
    }

    @Test
    void rejectsSessionIdPathTraversal() {
        JsonlSessionStore store = newStore();

        assertThatThrownBy(() -> store.findSession("../outside"))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Invalid sessionId");
        assertThatThrownBy(() -> store.loadMessages("..\\outside"))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Invalid sessionId");
    }

    @Test
    void corruptSessionMetadataFailsInsteadOfReturningEmpty() throws Exception {
        JsonlSessionStore store = newStore();
        AgentSession session = store.create(tempDir.resolve("workspace"));
        Files.writeString(sessionDir(session.id()).resolve("session.json"), "{\"id\":\"%s\"".formatted(session.id()));

        assertThatThrownBy(() -> store.findSession(session.id()))
                .isInstanceOf(SessionStoreException.class)
                .hasMessageContaining("Failed to read session metadata");
    }

    private JsonlSessionStore newStore() {
        return new JsonlSessionStore(new SessionProperties(tempDir.resolve("sessions")), objectMapper);
    }

    private Path sessionDir(String sessionId) {
        return tempDir.resolve("sessions").resolve(sessionId);
    }

    private static UserMessage sampleUserMessage(String sessionId) {
        return new UserMessage("m_1", sessionId, null, Instant.parse("2026-06-29T00:00:00Z"), "hello");
    }

    private static AgentStartedEvent sampleStartedEvent(String sessionId) {
        return new AgentStartedEvent("e_1", sessionId, Instant.parse("2026-06-29T00:00:00Z"));
    }

    private static void writeLine(Path path, String line) throws IOException {
        Files.writeString(path, line + System.lineSeparator());
    }
}
