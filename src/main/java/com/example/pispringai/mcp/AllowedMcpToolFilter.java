package com.example.pispringai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.Objects;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolFilter;
import org.springframework.stereotype.Component;

@Component
public class AllowedMcpToolFilter implements McpToolFilter {

    private final McpProperties properties;

    public AllowedMcpToolFilter(McpProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public boolean test(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
        if (connectionInfo == null || connectionInfo.initializeResult() == null || tool == null) {
            return false;
        }
        McpSchema.Implementation serverInfo = connectionInfo.initializeResult().serverInfo();
        return serverInfo != null && properties.allows(serverInfo.name(), tool.name());
    }
}
