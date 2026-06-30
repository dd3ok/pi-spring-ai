package com.example.pispringai.tool;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ToolOutputLimiter {

    private ToolOutputLimiter() {
    }

    public static ToolResult ok(String text, Map<String, Object> metadata, ToolProperties properties) {
        LimitedOutput limited = limit(text, properties.maxToolOutputChars());
        Map<String, Object> resultMetadata = new LinkedHashMap<>(metadata);
        if (limited.truncated()) {
            resultMetadata.put("truncated", true);
        }
        return ToolResult.ok(limited.text(), resultMetadata);
    }

    public static LimitedOutput limit(String text, int maxChars) {
        if (text.length() <= maxChars) {
            return new LimitedOutput(text, false);
        }
        return new LimitedOutput(text.substring(0, maxChars), true);
    }

    public record LimitedOutput(String text, boolean truncated) {
    }
}
