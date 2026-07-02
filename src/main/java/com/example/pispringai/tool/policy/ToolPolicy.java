package com.example.pispringai.tool.policy;

import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolResult;

public interface ToolPolicy {

    ToolPolicyDecision before(AgentTool tool, ToolArguments arguments, ToolExecutionContext context);

    ToolResult after(AgentTool tool, ToolArguments arguments, ToolResult result, ToolExecutionContext context);
}
