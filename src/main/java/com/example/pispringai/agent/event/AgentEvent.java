package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public sealed interface AgentEvent permits
        AgentStartedEvent,
        AgentCompletedEvent,
        UserMessageAppendedEvent,
        AssistantDeltaEvent,
        AssistantCompletedEvent,
        ToolCallStartedEvent,
        ToolCallFinishedEvent,
        ToolCallDeniedEvent,
        AgentErrorEvent {

    @JsonProperty("type")
    AgentEventType type();

    String eventId();

    String sessionId();

    Instant createdAt();
}
