package com.example.pispringai.tool;

import com.example.pispringai.tool.policy.ToolPolicy;
import com.example.pispringai.tool.policy.ToolPolicyDecision;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class ToolExecutionService {

    private final ToolPolicy toolPolicy;

    public ToolExecutionService(ToolPolicy toolPolicy) {
        this.toolPolicy = Objects.requireNonNull(toolPolicy, "toolPolicy must not be null");
    }

    public ToolResult execute(AgentTool tool, ToolArguments arguments, ToolExecutionContext context) {
        ToolPolicyDecision decision = toolPolicy.before(tool, arguments, context);
        if (decision.denied()) {
            ToolResult denied = ToolResult.error(decision.reason(), Map.of("policyDenied", true));
            return toolPolicy.after(tool, arguments, denied, context);
        }

        ToolResult result = tool.execute(arguments);
        return toolPolicy.after(tool, arguments, result, context);
    }
}
