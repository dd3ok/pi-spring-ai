package com.example.pispringai.tool;

public interface AgentTool {

    ToolSpec spec();

    ToolResult execute(ToolArguments arguments);

    default ToolResult execute(ToolArguments arguments, ToolExecutionContext context) {
        return execute(arguments);
    }
}
