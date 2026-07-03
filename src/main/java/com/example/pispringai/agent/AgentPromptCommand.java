package com.example.pispringai.agent;

import com.example.pispringai.agent.message.AgentMessage;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record AgentPromptCommand(
        String sessionId,
        String userText,
        Path workspaceRoot,
        AgentRunOptions options,
        List<AgentMessage> messages
) {

    public AgentPromptCommand(String sessionId, String userText, Path workspaceRoot, AgentRunOptions options) {
        this(sessionId, userText, workspaceRoot, options, List.of());
    }

    public AgentPromptCommand {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        if (userText == null || userText.isBlank()) {
            throw new IllegalArgumentException("userText must not be blank");
        }
        Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        Objects.requireNonNull(options, "options must not be null");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages must not be null"));
    }

    public AgentPromptCommand withMessages(List<AgentMessage> messages) {
        return new AgentPromptCommand(sessionId, userText, workspaceRoot, options, messages);
    }
}
