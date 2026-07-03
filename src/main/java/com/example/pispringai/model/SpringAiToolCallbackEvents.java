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

    default void toolCallStarted(String toolCallId, String toolName, ToolArguments arguments) {
        toolCallStarted(toolName, arguments);
    }

    default void toolCallFinished(String toolName, ToolResult result) {
    }

    default void toolCallFinished(String toolCallId, String toolName, ToolResult result) {
        toolCallFinished(toolName, result);
    }

    default void toolCallDenied(String toolName, ToolResult result) {
    }

    default void toolCallDenied(String toolCallId, String toolName, ToolResult result) {
        toolCallDenied(toolName, result);
    }

    default void toolCallFailed(String toolName, ToolResult result) {
    }

    default void toolCallFailed(String toolCallId, String toolName, ToolResult result) {
        toolCallFailed(toolName, result);
    }
}
