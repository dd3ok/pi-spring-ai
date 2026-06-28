package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record ToolCallDeniedEvent(
        String eventId,
        String sessionId,
        Instant createdAt,
        String callId,
        String tool,
        String reason
) implements AgentEvent {

    public ToolCallDeniedEvent {
        eventId = requireNonBlank(eventId, "eventId");
        sessionId = requireNonBlank(sessionId, "sessionId");
        createdAt = requireNonNull(createdAt, "createdAt");
        callId = requireNonBlank(callId, "callId");
        tool = requireNonBlank(tool, "tool");
        reason = requireNonBlank(reason, "reason");
    }

    @Override
    @JsonProperty("type")
    public AgentEventType type() {
        return AgentEventType.TOOL_CALL_DENIED;
    }
}
