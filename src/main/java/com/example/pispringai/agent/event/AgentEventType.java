package com.example.pispringai.agent.event;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AgentEventType {
    AGENT_STARTED("agent_started"),
    USER_MESSAGE_APPENDED("user_message_appended"),
    ASSISTANT_DELTA("assistant_delta"),
    ASSISTANT_COMPLETED("assistant_completed"),
    TOOL_CALL_STARTED("tool_call_started"),
    TOOL_CALL_FINISHED("tool_call_finished"),
    TOOL_CALL_DENIED("tool_call_denied"),
    AGENT_ERROR("agent_error"),
    AGENT_COMPLETED("agent_completed");

    private final String jsonValue;

    AgentEventType(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    @JsonValue
    public String jsonValue() {
        return jsonValue;
    }
}
