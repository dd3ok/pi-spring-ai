package com.example.pispringai.agent;

import java.time.Duration;
import java.util.Objects;

public record AgentRunOptions(
        boolean allowWrite,
        boolean allowBash,
        int maxTurns,
        int maxToolCalls,
        Duration timeout
) {

    public AgentRunOptions {
        Objects.requireNonNull(timeout, "timeout must not be null");
        if (maxTurns <= 0) {
            throw new IllegalArgumentException("maxTurns must be positive");
        }
        if (maxToolCalls <= 0) {
            throw new IllegalArgumentException("maxToolCalls must be positive");
        }
        if (!timeout.isPositive()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static AgentRunOptions safeDefaults() {
        return new AgentRunOptions(false, false, 6, 12, Duration.ofSeconds(120));
    }
}
