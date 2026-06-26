package com.example.pispringai.agent.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlankWhenProvided;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record ToolResultMessage(
        String id,
        String sessionId,
        String parentId,
        Instant createdAt,
        String toolCallId,
        String toolName,
        String resultText,
        boolean error
) implements AgentMessage {

    public ToolResultMessage {
        id = requireNonBlank(id, "id");
        sessionId = requireNonBlank(sessionId, "sessionId");
        parentId = requireNonBlankWhenProvided(parentId, "parentId");
        createdAt = requireNonNull(createdAt, "createdAt");
        toolCallId = requireNonBlank(toolCallId, "toolCallId");
        toolName = requireNonBlank(toolName, "toolName");
        resultText = requireNonNull(resultText, "resultText");
    }

    @Override
    @JsonProperty("kind")
    public AgentMessageKind kind() {
        return AgentMessageKind.TOOL_RESULT;
    }
}
