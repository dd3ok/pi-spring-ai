package com.example.pispringai.api;

import com.example.pispringai.agent.AgentRunOptions;

public record AgentPromptRequest(String text, Boolean allowWrite, Boolean allowBash, Boolean allowMcp) {

    public AgentPromptRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
    }

    public AgentRunOptions toRunOptions() {
        AgentRunOptions defaults = AgentRunOptions.safeDefaults();
        return new AgentRunOptions(
                Boolean.TRUE.equals(allowWrite),
                Boolean.TRUE.equals(allowBash),
                Boolean.TRUE.equals(allowMcp),
                defaults.maxTurns(),
                defaults.maxToolCalls(),
                defaults.timeout());
    }
}
