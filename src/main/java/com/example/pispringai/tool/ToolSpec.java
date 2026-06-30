package com.example.pispringai.tool;

import java.util.Map;
import java.util.Objects;

public record ToolSpec(
        String name,
        String description,
        Map<String, Object> inputSchema,
        ToolSafetyLevel safetyLevel,
        boolean enabledByDefault
) {

    public ToolSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        inputSchema = Map.copyOf(Objects.requireNonNull(inputSchema, "inputSchema must not be null"));
        Objects.requireNonNull(safetyLevel, "safetyLevel must not be null");
    }
}
