package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record ToolCallFinishedEvent(
        String eventId,
        String sessionId,
        Instant createdAt,
        String callId,
        String tool,
        boolean error
) implements AgentEvent {

    public ToolCallFinishedEvent {
        eventId = requireNonBlank(eventId, "eventId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        createdAt = requireNonNull(createdAt, "createdAt");
        callId = requireNonBlank(callId, "callId");
        tool = requireNonBlank(tool, "tool");
    }

    @Override
    @JsonProperty("type")
    public AgentEventType type() {
        return AgentEventType.TOOL_CALL_FINISHED;
    }
}
