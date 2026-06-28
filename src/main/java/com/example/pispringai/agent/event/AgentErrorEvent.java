package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record AgentErrorEvent(
        String eventId,
        String sessionId,
        Instant createdAt,
        String message,
        String errorCode,
        boolean recoverable
) implements AgentEvent {

    public AgentErrorEvent {
        eventId = requireNonBlank(eventId, "eventId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        createdAt = requireNonNull(createdAt, "createdAt");
        message = requireNonBlank(message, "message");
        errorCode = requireNonBlank(errorCode, "errorCode");
    }

    @Override
    @JsonProperty("type")
    public AgentEventType type() {
        return AgentEventType.AGENT_ERROR;
    }
}
