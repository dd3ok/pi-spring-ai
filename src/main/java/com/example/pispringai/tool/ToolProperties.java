package com.example.pispringai.tool;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.tools")
public record ToolProperties(int maxToolOutputChars) {

    private static final int DEFAULT_MAX_TOOL_OUTPUT_CHARS = 20_000;

    public ToolProperties {
        maxToolOutputChars = maxToolOutputChars > 0 ? maxToolOutputChars : DEFAULT_MAX_TOOL_OUTPUT_CHARS;
    }
}
