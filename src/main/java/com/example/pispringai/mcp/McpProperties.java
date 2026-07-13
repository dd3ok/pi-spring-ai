package com.example.pispringai.mcp;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.mcp")
public record McpProperties(Set<String> allowedTools) {

    public McpProperties {
        allowedTools = allowedTools == null
                ? Set.of()
                : allowedTools.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet());
    }

    public boolean allows(String serverName, String toolName) {
        if (serverName == null || serverName.isBlank() || toolName == null || toolName.isBlank()) {
            return false;
        }
        return allowedTools.contains(serverName + "/" + toolName);
    }
}
