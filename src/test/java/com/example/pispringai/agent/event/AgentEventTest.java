package com.example.pispringai.agent.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentEventTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void lifecycleEventsExposeStableTypes() throws Exception {
        AgentStartedEvent started = new AgentStartedEvent(
                "e_1",
                "s_1",
                Instant.parse("2026-06-26T00:00:00Z"));
        AgentCompletedEvent completed = new AgentCompletedEvent(
                "e_2",
                "s_1",
                Instant.parse("2026-06-26T00:00:10Z"));

        assertThat(started).isInstanceOf(AgentEvent.class);
        assertThat(started.type()).isEqualTo(AgentEventType.AGENT_STARTED);
        assertThat(completed.type()).isEqualTo(AgentEventType.AGENT_COMPLETED);

        JsonNode startedJson = objectMapper.valueToTree(started);
        JsonNode completedJson = objectMapper.valueToTree(completed);

        assertThat(fieldNames(startedJson))
                .containsExactlyInAnyOrder("type", "eventId", "sessionId", "createdAt");
        assertThat(startedJson.get("type").asText()).isEqualTo("agent_started");
        assertThat(startedJson.get("eventId").asText()).isEqualTo("e_1");
        assertThat(startedJson.get("sessionId").asText()).isEqualTo("s_1");
        assertThat(completedJson.get("type").asText()).isEqualTo("agent_completed");
    }

    @Test
    void messageAndAssistantEventsExposeStableTypes() throws Exception {
        UserMessageAppendedEvent userMessage = new UserMessageAppendedEvent(
                "e_3",
                "s_1",
                Instant.parse("2026-06-26T00:00:01Z"),
                "m_1");
        AssistantDeltaEvent delta = new AssistantDeltaEvent(
                "e_4",
                "s_1",
                Instant.parse("2026-06-26T00:00:02Z"),
                "Hello");
        AssistantCompletedEvent assistantCompleted = new AssistantCompletedEvent(
                "e_5",
                "s_1",
                Instant.parse("2026-06-26T00:00:03Z"),
                "m_2");

        assertThat(userMessage.type()).isEqualTo(AgentEventType.USER_MESSAGE_APPENDED);
        assertThat(delta.type()).isEqualTo(AgentEventType.ASSISTANT_DELTA);
        assertThat(assistantCompleted.type()).isEqualTo(AgentEventType.ASSISTANT_COMPLETED);

        JsonNode userMessageJson = objectMapper.valueToTree(userMessage);
        JsonNode deltaJson = objectMapper.valueToTree(delta);
        JsonNode assistantCompletedJson = objectMapper.valueToTree(assistantCompleted);

        assertThat(fieldNames(userMessageJson))
                .containsExactlyInAnyOrder("type", "eventId", "sessionId", "createdAt", "messageId");
        assertThat(userMessageJson.get("type").asText()).isEqualTo("user_message_appended");
        assertThat(userMessageJson.get("messageId").asText()).isEqualTo("m_1");
        assertThat(fieldNames(deltaJson))
                .containsExactlyInAnyOrder("type", "eventId", "sessionId", "createdAt", "text");
        assertThat(deltaJson.get("type").asText()).isEqualTo("assistant_delta");
        assertThat(deltaJson.get("text").asText()).isEqualTo("Hello");
        assertThat(assistantCompletedJson.get("type").asText()).isEqualTo("assistant_completed");
        assertThat(assistantCompletedJson.get("messageId").asText()).isEqualTo("m_2");
    }

    @Test
    void toolCallEventsExposeToolFieldsAndStableTypes() throws Exception {
        ToolCallStartedEvent started = new ToolCallStartedEvent(
                "e_6",
                "s_1",
                Instant.parse("2026-06-26T00:00:04Z"),
                "tc_1",
                "read");
        ToolCallFinishedEvent finished = new ToolCallFinishedEvent(
                "e_7",
                "s_1",
                Instant.parse("2026-06-26T00:00:05Z"),
                "tc_1",
                "read",
                false);
        ToolCallDeniedEvent denied = new ToolCallDeniedEvent(
                "e_8",
                "s_1",
                Instant.parse("2026-06-26T00:00:06Z"),
                "tc_2",
                "read",
                "Path escapes workspace");

        assertThat(started.type()).isEqualTo(AgentEventType.TOOL_CALL_STARTED);
        assertThat(finished.type()).isEqualTo(AgentEventType.TOOL_CALL_FINISHED);
        assertThat(denied.type()).isEqualTo(AgentEventType.TOOL_CALL_DENIED);

        JsonNode startedJson = objectMapper.valueToTree(started);
        JsonNode finishedJson = objectMapper.valueToTree(finished);
        JsonNode deniedJson = objectMapper.valueToTree(denied);

        assertThat(fieldNames(startedJson))
                .containsExactlyInAnyOrder("type", "eventId", "sessionId", "createdAt", "callId", "tool");
        assertThat(startedJson.get("type").asText()).isEqualTo("tool_call_started");
        assertThat(startedJson.get("callId").asText()).isEqualTo("tc_1");
        assertThat(startedJson.get("tool").asText()).isEqualTo("read");
        assertThat(fieldNames(finishedJson))
                .containsExactlyInAnyOrder("type", "eventId", "sessionId", "createdAt", "callId", "tool", "error");
        assertThat(finishedJson.get("type").asText()).isEqualTo("tool_call_finished");
        assertThat(finishedJson.get("error").asBoolean()).isFalse();
        assertThat(fieldNames(deniedJson))
                .containsExactlyInAnyOrder("type", "eventId", "sessionId", "createdAt", "callId", "tool", "reason");
        assertThat(deniedJson.get("type").asText()).isEqualTo("tool_call_denied");
        assertThat(deniedJson.get("reason").asText()).isEqualTo("Path escapes workspace");
    }

    @Test
    void agentErrorEventCarriesRecoverability() throws Exception {
        AgentErrorEvent event = new AgentErrorEvent(
                "e_9",
                "s_1",
                Instant.parse("2026-06-26T00:00:07Z"),
                "Model stream failed",
                "MODEL_STREAM_FAILED",
                true);

        assertThat(event.type()).isEqualTo(AgentEventType.AGENT_ERROR);
        assertThat(event.recoverable()).isTrue();

        JsonNode json = objectMapper.valueToTree(event);

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder(
                        "type", "eventId", "sessionId", "createdAt", "message", "errorCode", "recoverable");
        assertThat(json.get("type").asText()).isEqualTo("agent_error");
        assertThat(json.get("message").asText()).isEqualTo("Model stream failed");
        assertThat(json.get("errorCode").asText()).isEqualTo("MODEL_STREAM_FAILED");
        assertThat(json.get("recoverable").asBoolean()).isTrue();
    }

    @Test
    void eventsRejectInvalidRequiredFields() {
        Instant now = Instant.parse("2026-06-26T00:00:00Z");

        assertThatThrownBy(() -> new AgentStartedEvent(" ", "s_1", now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventId must not be blank");
        assertThatThrownBy(() -> new AgentStartedEvent("e_1", " ", now))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sessionId must not be blank");
        assertThatThrownBy(() -> new AgentStartedEvent("e_1", "s_1", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("createdAt must not be null");
        assertThatThrownBy(() -> new UserMessageAppendedEvent("e_1", "s_1", now, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("messageId must not be blank");
        assertThatThrownBy(() -> new AssistantDeltaEvent("e_1", "s_1", now, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
        assertThatThrownBy(() -> new ToolCallStartedEvent("e_1", "s_1", now, " ", "read"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("callId must not be blank");
        assertThatThrownBy(() -> new ToolCallStartedEvent("e_1", "s_1", now, "tc_1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("tool must not be blank");
        assertThatThrownBy(() -> new ToolCallDeniedEvent("e_1", "s_1", now, "tc_1", "read", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("reason must not be blank");
        assertThatThrownBy(() -> new AgentErrorEvent("e_1", "s_1", now, " ", "MODEL_ERROR", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("message must not be blank");
        assertThatThrownBy(() -> new AgentErrorEvent("e_1", "s_1", now, "failed", " ", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("errorCode must not be blank");
    }

    private static List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
