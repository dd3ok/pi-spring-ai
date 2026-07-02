package com.example.pispringai.model;

import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolResult;

public interface SpringAiToolCallbackEvents {

    static SpringAiToolCallbackEvents noop() {
        return new SpringAiToolCallbackEvents() {
        };
    }

    default void toolCallStarted(String toolName, ToolArguments arguments) {
    }

    default void toolCallFinished(String toolName, ToolResult result) {
    }

    default void toolCallDenied(String toolName, ToolResult result) {
    }

    default void toolCallFailed(String toolName, ToolResult result) {
    }
}
