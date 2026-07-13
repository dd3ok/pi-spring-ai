package com.example.pispringai.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class McpToolBridge {

    private final ObjectProvider<SyncMcpToolCallbackProvider> callbackProvider;
    private final ObjectMapper objectMapper;

    public McpToolBridge(
            ObjectProvider<SyncMcpToolCallbackProvider> callbackProvider,
            ObjectMapper objectMapper
    ) {
        this.callbackProvider = Objects.requireNonNull(callbackProvider, "callbackProvider must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public List<McpAgentTool> tools() {
        SyncMcpToolCallbackProvider provider = callbackProvider.getIfAvailable();
        if (provider == null) {
            return List.of();
        }
        return Arrays.stream(provider.getToolCallbacks())
                .map(callback -> new McpAgentTool(callback, objectMapper))
                .toList();
    }
}
