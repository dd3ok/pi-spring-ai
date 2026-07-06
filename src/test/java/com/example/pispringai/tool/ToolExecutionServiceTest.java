package com.example.pispringai.tool;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.policy.DefaultToolPolicy;
import com.example.pispringai.tool.policy.ToolPolicy;
import com.example.pispringai.tool.policy.ToolPolicyDecision;
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
    void doesNotRunAfterPolicyWhenBeforePolicyDeniesIt() {
        CountingTool tool = new CountingTool("write", ToolSafetyLevel.WRITE, ToolResult.ok("should not run"));
        TrackingPolicy policy = new TrackingPolicy(ToolPolicyDecision.deny("denied by policy"));
        ToolExecutionService service = new ToolExecutionService(policy);

        ToolResult result = service.execute(tool, NO_ARGUMENTS, SAFE_CONTEXT);

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("denied by policy");
        assertThat(result.metadata()).containsEntry("policyDenied", true);
        assertThat(tool.executionCount()).isZero();
        assertThat(policy.beforeCount()).isEqualTo(1);
        assertThat(policy.afterCount()).isZero();
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

    @Test
    void convertsRuntimeExceptionFromToolIntoErrorResult() {
        ThrowingTool tool = new ThrowingTool(new IllegalStateException("boom"));
        TrackingPolicy policy = new TrackingPolicy(ToolPolicyDecision.allow());
        ToolExecutionService service = new ToolExecutionService(policy);

        ToolResult result = service.execute(tool, NO_ARGUMENTS, SAFE_CONTEXT);

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("tool execution failed", "boom");
        assertThat(result.metadata())
                .containsEntry("exception", IllegalStateException.class.getName())
                .containsEntry("exceptionMessage", "boom");
        assertThat(policy.beforeCount()).isEqualTo(1);
        assertThat(policy.afterCount()).isEqualTo(1);
    }

    private static ToolExecutionService serviceWithMaxOutputChars(int maxOutputChars) {
        return new ToolExecutionService(new DefaultToolPolicy(new ToolProperties(maxOutputChars, 30)));
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

    private record ThrowingTool(RuntimeException exception) implements AgentTool {

        @Override
        public ToolSpec spec() {
            return new ToolSpec("read", "throwing tool", Map.of(), ToolSafetyLevel.READ_ONLY, true);
        }

        @Override
        public ToolResult execute(ToolArguments arguments) {
            throw exception;
        }
    }

    private static final class TrackingPolicy implements ToolPolicy {

        private final ToolPolicyDecision beforeDecision;
        private int beforeCount;
        private int afterCount;

        private TrackingPolicy(ToolPolicyDecision beforeDecision) {
            this.beforeDecision = beforeDecision;
        }

        @Override
        public ToolPolicyDecision before(AgentTool tool, ToolArguments arguments, ToolExecutionContext context) {
            beforeCount++;
            return beforeDecision;
        }

        @Override
        public ToolResult after(AgentTool tool, ToolArguments arguments, ToolResult result, ToolExecutionContext context) {
            afterCount++;
            return result;
        }

        private int beforeCount() {
            return beforeCount;
        }

        private int afterCount() {
            return afterCount;
        }
    }
}
