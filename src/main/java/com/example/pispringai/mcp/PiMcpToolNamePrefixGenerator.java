package com.example.pispringai.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.ai.mcp.McpConnectionInfo;
import org.springframework.ai.mcp.McpToolNamePrefixGenerator;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.stereotype.Component;

@Component
public class PiMcpToolNamePrefixGenerator implements McpToolNamePrefixGenerator {

    private static final String PREFIX = "mcp_";
    private static final int MAX_TOOL_NAME_LENGTH = 64;

    @Override
    public String prefixedToolName(McpConnectionInfo connectionInfo, McpSchema.Tool tool) {
        if (connectionInfo == null || connectionInfo.initializeResult() == null) {
            throw new IllegalStateException("MCP server must be initialized before tools are named");
        }
        McpSchema.Implementation serverInfo = connectionInfo.initializeResult().serverInfo();
        if (serverInfo == null || serverInfo.name() == null || serverInfo.name().isBlank()) {
            throw new IllegalStateException("MCP server name must not be blank");
        }
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            throw new IllegalArgumentException("MCP tool name must not be blank");
        }

        String serverName = McpToolUtils.format(serverInfo.name());
        String toolName = McpToolUtils.format(tool.name());
        if (serverName.isBlank() || toolName.isBlank()) {
            throw new IllegalArgumentException("MCP server and tool names must contain supported characters");
        }
        String formatted = PREFIX + serverName + "_" + toolName;
        if (formatted.length() <= MAX_TOOL_NAME_LENGTH) {
            return formatted;
        }
        return PREFIX + formatted.substring(formatted.length() - (MAX_TOOL_NAME_LENGTH - PREFIX.length()));
    }
}
