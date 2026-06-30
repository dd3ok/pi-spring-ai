package com.example.pispringai.tool;

import java.util.Map;
import java.util.Objects;

public record ToolResult(
        String text,
        boolean error,
        Map<String, Object> metadata
) {

    public ToolResult {
        text = Objects.requireNonNull(text, "text must not be null");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata must not be null"));
    }

    public static ToolResult ok(String text) {
        return ok(text, Map.of());
    }

    public static ToolResult ok(String text, Map<String, Object> metadata) {
        return new ToolResult(text, false, metadata);
    }

    public static ToolResult error(String text) {
        return error(text, Map.of());
    }

    public static ToolResult error(String text, Map<String, Object> metadata) {
        return new ToolResult(text, true, metadata);
    }
}
