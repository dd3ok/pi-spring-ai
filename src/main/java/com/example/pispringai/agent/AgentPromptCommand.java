package com.example.pispringai.agent;

import java.nio.file.Path;
import java.util.Objects;

public record AgentPromptCommand(
        String sessionId,
        String userText,
        Path workspaceRoot,
        AgentRunOptions options
) {

    public AgentPromptCommand {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("userText must not be blank");
        }
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        Objects.requireNonNull(options, "options must not be null");
    }
}
