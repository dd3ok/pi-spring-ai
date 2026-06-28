package com.example.pispringai.agent.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public sealed interface AgentMessage
        permits UserMessage, AssistantMessage, ToolResultMessage {

    @JsonProperty("kind")
    AgentMessageKind kind();

    String id();

    String sessionId();

    String parentId();

    Instant createdAt();
}
