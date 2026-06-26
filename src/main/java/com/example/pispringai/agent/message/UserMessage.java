package com.example.pispringai.agent.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlankWhenProvided;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record UserMessage(
        String id,
        String sessionId,
        String parentId,
        Instant createdAt,
        String text
) implements AgentMessage {

    public UserMessage {
        id = requireNonBlank(id, "id");
        sessionId = requireNonBlank(sessionId, "sessionId");
        parentId = requireNonBlankWhenProvided(parentId, "parentId");
        createdAt = requireNonNull(createdAt, "createdAt");
        text = requireNonNull(text, "text");
    }

    @Override
    @JsonProperty("kind")
    public AgentMessageKind kind() {
        return AgentMessageKind.USER;
    }
}
