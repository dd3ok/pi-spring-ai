package com.example.pispringai.tool.policy;

public record ToolPolicyDecision(boolean allowed, String reason) {

    public ToolPolicyDecision {
        if (allowed) {
            reason = reason == null ? "" : reason;
        } else if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("deny reason must not be blank");
        }
    }

    public static ToolPolicyDecision allow() {
        return new ToolPolicyDecision(true, "");
    }

    public static ToolPolicyDecision deny(String reason) {
        return new ToolPolicyDecision(false, reason);
    }

    public boolean denied() {
        return !allowed;
    }
}
