package com.example.pispringai.tool.policy;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.ToolSpec;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultToolPolicyTest {

    private static final ToolArguments NO_ARGUMENTS = new ToolArguments(Map.of());

    private final ToolPolicy policy = new DefaultToolPolicy(new ToolProperties(5, 30));

    @Test
    void allowsReadOnlyToolWithSafeDefaults() {
        ToolPolicyDecision decision = policy.before(
                tool("read", ToolSafetyLevel.READ_ONLY),
                NO_ARGUMENTS,
                context(AgentRunOptions.safeDefaults()));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.denied()).isFalse();
    }

    @Test
    void deniesWriteToolWhenAllowWriteIsFalse() {
        ToolPolicyDecision decision = policy.before(
                tool("write", ToolSafetyLevel.WRITE),
                NO_ARGUMENTS,
                context(AgentRunOptions.safeDefaults()));

        assertThat(decision.denied()).isTrue();
        assertThat(decision.reason()).contains("allowWrite");
    }

    @Test
    void deniesBashToolWhenAllowBashIsFalse() {
        ToolPolicyDecision decision = policy.before(
                tool("bash", ToolSafetyLevel.BASH),
                NO_ARGUMENTS,
                context(AgentRunOptions.safeDefaults()));

        assertThat(decision.denied()).isTrue();
        assertThat(decision.reason()).contains("allowBash");
    }

    @Test
    void deniesMcpToolWhenAllowMcpIsFalse() {
        ToolPolicyDecision decision = policy.before(
                tool("mcp_demo_echo", ToolSafetyLevel.MCP),
                NO_ARGUMENTS,
                context(AgentRunOptions.safeDefaults()));

        assertThat(decision.denied()).isTrue();
        assertThat(decision.reason()).contains("allowMcp");
    }

    @Test
    void allowsWriteAndBashToolsWhenRunOptionsAllowThem() {
        ToolExecutionContext context = context(new AgentRunOptions(
                true,
                true,
                6,
                12,
                Duration.ofSeconds(120)));

        assertThat(policy.before(tool("write", ToolSafetyLevel.WRITE), NO_ARGUMENTS, context).allowed())
                .isTrue();
        assertThat(policy.before(tool("bash", ToolSafetyLevel.BASH), NO_ARGUMENTS, context).allowed())
                .isTrue();
    }

    @Test
    void allowsMcpToolWhenRunOptionsAllowIt() {
        ToolExecutionContext context = context(new AgentRunOptions(
                false,
                false,
                true,
                6,
                12,
                Duration.ofSeconds(120)));

        assertThat(policy.before(tool("mcp_demo_echo", ToolSafetyLevel.MCP), NO_ARGUMENTS, context).allowed())
                .isTrue();
    }

    @Test
    void truncatesToolResultTextAfterExecution() {
        ToolResult result = policy.after(
                tool("read", ToolSafetyLevel.READ_ONLY),
                NO_ARGUMENTS,
                ToolResult.ok("abcdef", Map.of("source", "stub")),
                context(AgentRunOptions.safeDefaults()));

        assertThat(result.text()).isEqualTo("abcde");
        assertThat(result.error()).isFalse();
        assertThat(result.metadata()).containsEntry("source", "stub");
        assertThat(result.metadata()).containsEntry("truncated", true);
    }

    private static ToolExecutionContext context(AgentRunOptions options) {
        return new ToolExecutionContext(options);
    }

    private static AgentTool tool(String name, ToolSafetyLevel safetyLevel) {
        return new StubTool(name, safetyLevel);
    }

    private record StubTool(String name, ToolSafetyLevel safetyLevel) implements AgentTool {

        @Override
        public ToolSpec spec() {
            return new ToolSpec(name, "stub tool", Map.of(), safetyLevel, true);
        }

        @Override
        public ToolResult execute(ToolArguments arguments) {
            return ToolResult.ok("ok");
        }
    }
}
