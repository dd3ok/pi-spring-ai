package com.example.pispringai.tool;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.policy.DefaultToolPolicy;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolExecutionServiceTest {

    private static final ToolArguments NO_ARGUMENTS = new ToolArguments(Map.of());
    private static final ToolExecutionContext SAFE_CONTEXT = new ToolExecutionContext(AgentRunOptions.safeDefaults());

    @Test
    void returnsAllowedToolResult() {
        CountingTool tool = new CountingTool("read", ToolSafetyLevel.READ_ONLY, ToolResult.ok("ok"));
        ToolExecutionService service = serviceWithMaxOutputChars(100);

        ToolResult result = service.execute(tool, NO_ARGUMENTS, SAFE_CONTEXT);

        assertThat(result.text()).isEqualTo("ok");
        assertThat(result.error()).isFalse();
        assertThat(tool.executionCount()).isEqualTo(1);
    }

    @Test
    void returnsErrorWithoutExecutingToolWhenPolicyDeniesIt() {
        CountingTool tool = new CountingTool("write", ToolSafetyLevel.WRITE, ToolResult.ok("should not run"));
        ToolExecutionService service = serviceWithMaxOutputChars(100);

        ToolResult result = service.execute(tool, NO_ARGUMENTS, SAFE_CONTEXT);

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("allowWrite");
        assertThat(tool.executionCount()).isZero();
    }

    @Test
    void appliesCommonOutputLimitAfterAllowedToolRuns() {
        CountingTool tool = new CountingTool("read", ToolSafetyLevel.READ_ONLY, ToolResult.ok("abcdef"));
        ToolExecutionService service = serviceWithMaxOutputChars(4);

        ToolResult result = service.execute(tool, NO_ARGUMENTS, SAFE_CONTEXT);

        assertThat(result.text()).isEqualTo("abcd");
        assertThat(result.metadata()).containsEntry("truncated", true);
        assertThat(tool.executionCount()).isEqualTo(1);
    }

    private static ToolExecutionService serviceWithMaxOutputChars(int maxOutputChars) {
        return new ToolExecutionService(new DefaultToolPolicy(new ToolProperties(maxOutputChars)));
    }

    private static final class CountingTool implements AgentTool {

        private final String name;
        private final ToolSafetyLevel safetyLevel;
        private final ToolResult result;
        private int executionCount;

        private CountingTool(String name, ToolSafetyLevel safetyLevel, ToolResult result) {
            this.name = name;
            this.safetyLevel = safetyLevel;
            this.result = result;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec(name, "stub tool", Map.of(), safetyLevel, true);
        }

        @Override
        public ToolResult execute(ToolArguments arguments) {
            executionCount++;
            return result;
        }

        private int executionCount() {
            return executionCount;
        }
    }
}
