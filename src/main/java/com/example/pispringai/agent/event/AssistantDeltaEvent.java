package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record AssistantDeltaEvent(
        String eventId,
        String sessionId,
        Instant createdAt,
        String text
) implements AgentEvent {

    public AssistantDeltaEvent {
        eventId = requireNonBlank(eventId, "eventId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        createdAt = requireNonNull(createdAt, "createdAt");
        text = requireNonNull(text, "text");
    }

    @Override
    @JsonProperty("type")
    public AgentEventType type() {
        return AgentEventType.ASSISTANT_DELTA;
    }
}
