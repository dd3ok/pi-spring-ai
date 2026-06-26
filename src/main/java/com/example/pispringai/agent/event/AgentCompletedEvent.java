package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record AgentCompletedEvent(
        String eventId,
        String sessionId,
        Instant createdAt
) implements AgentEvent {

    public AgentCompletedEvent {
        eventId = requireNonBlank(eventId, "eventId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        createdAt = requireNonNull(createdAt, "createdAt");
    }

    @Override
    @JsonProperty("type")
    public AgentEventType type() {
        return AgentEventType.AGENT_COMPLETED;
    }
}
