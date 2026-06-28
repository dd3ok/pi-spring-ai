package com.example.pispringai.agent.message;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AgentMessageKind {
    USER("user"),
    ASSISTANT("assistant"),
    TOOL_RESULT("tool_result");

    private final String jsonValue;

    AgentMessageKind(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
