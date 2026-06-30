package com.example.pispringai.tool;

public interface AgentTool {

    ToolSpec spec();

    ToolResult execute(ToolArguments arguments);
}
