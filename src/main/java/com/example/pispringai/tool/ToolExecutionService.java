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
            return ToolResult.error(decision.reason(), Map.of("policyDenied", true));
        }

        ToolResult result;
        try {
            result = tool.execute(arguments);
        } catch (RuntimeException exception) {
            result = ToolResult.error(
                    "tool execution failed: " + exceptionMessage(exception),
                    Map.of(
                            "exception", exception.getClass().getName(),
                            "exceptionMessage", exceptionMessage(exception)));
        }
        return toolPolicy.after(tool, arguments, result, context);
    }

    private static String exceptionMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
