package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record UserMessageAppendedEvent(
        String eventId,
        String sessionId,
        Instant createdAt,
        String messageId
) implements AgentEvent {

    public UserMessageAppendedEvent {
        eventId = requireNonBlank(eventId, "eventId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        createdAt = requireNonNull(createdAt, "createdAt");
        messageId = requireNonBlank(messageId, "messageId");
    }

    @Override
    @JsonProperty("type")
    public AgentEventType type() {
        return AgentEventType.USER_MESSAGE_APPENDED;
    }
}
