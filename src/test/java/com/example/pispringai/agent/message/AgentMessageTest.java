package com.example.pispringai.agent.message;

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

class AgentMessageTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void userMessageCarriesConversationFieldsAndSerializesKind() throws Exception {
        UserMessage message = new UserMessage(
                "m_1",
                "s_1",
                null,
                Instant.parse("2026-06-26T00:00:00Z"),
                "Analyze this project");

        assertThat(message).isInstanceOf(AgentMessage.class);
        assertThat(message.kind()).isEqualTo(AgentMessageKind.USER);

        JsonNode json = objectMapper.valueToTree(message);

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("kind", "id", "sessionId", "parentId", "createdAt", "text");
        assertThat(json.get("kind").asText()).isEqualTo("user");
        assertThat(json.get("id").asText()).isEqualTo("m_1");
        assertThat(json.get("sessionId").asText()).isEqualTo("s_1");
        assertThat(json.get("parentId").isNull()).isTrue();
        assertThat(json.get("createdAt").asText()).isEqualTo("2026-06-26T00:00:00Z");
        assertThat(json.get("text").asText()).isEqualTo("Analyze this project");
    }

    @Test
    void assistantMessageCarriesStopReasonAndSerializesKind() throws Exception {
        AssistantMessage message = new AssistantMessage(
                "m_2",
                "s_1",
                "m_1",
                Instant.parse("2026-06-26T00:00:01Z"),
                "This is a Spring Boot project.",
                AssistantStopReason.STOP);

        assertThat(message.kind()).isEqualTo(AgentMessageKind.ASSISTANT);
        assertThat(message.stopReason()).isEqualTo(AssistantStopReason.STOP);

        JsonNode json = objectMapper.valueToTree(message);

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder("kind", "id", "sessionId", "parentId", "createdAt", "text", "stopReason");
        assertThat(json.get("kind").asText()).isEqualTo("assistant");
        assertThat(json.get("parentId").asText()).isEqualTo("m_1");
        assertThat(json.get("stopReason").asText()).isEqualTo("STOP");
    }

    @Test
    void toolResultMessageCarriesToolResultFieldsAndSerializesKind() throws Exception {
        ToolResultMessage message = new ToolResultMessage(
                "m_3",
                "s_1",
                "m_2",
                Instant.parse("2026-06-26T00:00:02Z"),
                "tc_1",
                "read",
                "README.md contents",
                false);

        assertThat(message.kind()).isEqualTo(AgentMessageKind.TOOL_RESULT);
        assertThat(message.error()).isFalse();

        JsonNode json = objectMapper.valueToTree(message);

        assertThat(fieldNames(json))
                .containsExactlyInAnyOrder(
                        "kind",
                        "id",
                        "sessionId",
                        "parentId",
                        "createdAt",
                        "toolCallId",
                        "toolName",
                        "resultText",
                        "error");
        assertThat(json.get("kind").asText()).isEqualTo("tool_result");
        assertThat(json.get("toolCallId").asText()).isEqualTo("tc_1");
        assertThat(json.get("toolName").asText()).isEqualTo("read");
        assertThat(json.get("resultText").asText()).isEqualTo("README.md contents");
        assertThat(json.get("error").asBoolean()).isFalse();
    }

    @Test
    void messagesRejectInvalidRequiredFields() {
        Instant now = Instant.parse("2026-06-26T00:00:00Z");

        assertThatThrownBy(() -> new UserMessage(" ", "s_1", null, now, "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("id must not be blank");
        assertThatThrownBy(() -> new UserMessage("m_1", " ", null, now, "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sessionId must not be blank");
        assertThatThrownBy(() -> new UserMessage("m_1", "s_1", " ", now, "text"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("parentId must not be blank when provided");
        assertThatThrownBy(() -> new UserMessage("m_1", "s_1", null, null, "text"))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("createdAt must not be null");
        assertThatThrownBy(() -> new UserMessage("m_1", "s_1", null, now, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("text must not be null");
        assertThatThrownBy(() -> new AssistantMessage("m_2", "s_1", "m_1", now, "text", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("stopReason must not be null");
        assertThatThrownBy(() -> new ToolResultMessage("m_3", "s_1", "m_2", now, " ", "read", "result", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolCallId must not be blank");
        assertThatThrownBy(() -> new ToolResultMessage("m_3", "s_1", "m_2", now, "tc_1", " ", "result", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("toolName must not be blank");
        assertThatThrownBy(() -> new ToolResultMessage("m_3", "s_1", "m_2", now, "tc_1", "read", null, false))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("resultText must not be null");
    }

    private static List<String> fieldNames(JsonNode json) {
        List<String> names = new ArrayList<>();
        json.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
