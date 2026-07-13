package com.example.pispringai.mcp;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.mcp.server")
public record McpServerExposureProperties(
        List<String> exposedTools,
        boolean allowWrite,
        boolean allowBash
) {

    private static final List<String> DEFAULT_EXPOSED_TOOLS = List.of("ls", "read", "find", "grep");

    public McpServerExposureProperties {
        List<String> configuredTools = exposedTools == null ? DEFAULT_EXPOSED_TOOLS : exposedTools;
        Set<String> normalizedTools = new LinkedHashSet<>();
        for (String configuredTool : configuredTools) {
            if (configuredTool == null || configuredTool.isBlank()) {
                throw new IllegalArgumentException("MCP server exposed tool name must not be blank");
            }
            String toolName = configuredTool.trim();
            if (!normalizedTools.add(toolName)) {
                throw new IllegalArgumentException("duplicate MCP server exposed tool: " + toolName);
            }
        }
        exposedTools = List.copyOf(normalizedTools);
    }
}
