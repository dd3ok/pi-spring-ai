package com.example.pispringai.tool;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ToolArguments(Map<String, Object> values) {

    public ToolArguments {
        values = Map.copyOf(Objects.requireNonNull(values, "values must not be null"));
    }

    public String requiredString(String name) {
        return optionalString(name)
                .orElseThrow(() -> new IllegalArgumentException("missing required string argument: " + name));
    }

    public Optional<String> optionalString(String name) {
        Object value = values.get(name);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String stringValue) || stringValue.isBlank()) {
            throw new IllegalArgumentException("argument must be a non-blank string: " + name);
        }
        return Optional.of(stringValue);
    }
}
