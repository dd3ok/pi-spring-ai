package com.example.pispringai.tool;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.WorkspaceProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolRegistryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(ToolRegistrySpringConfig.class);

    @Test
    void returnsRegisteredReadOnlyToolsByName() {
        AgentTool read = new StubTool("read");
        AgentTool ls = new StubTool("ls");
        ToolRegistry registry = new ToolRegistry(List.of(read, ls));

        assertThat(registry.enabledTools()).containsExactly(read, ls);
        assertThat(registry.get("read")).isSameAs(read);
        assertThat(registry.get("ls")).isSameAs(ls);
    }

    @Test
    void filtersEnabledToolsByRunOptionsSafetyFlags() {
        AgentTool read = new StubTool("read", ToolSafetyLevel.READ_ONLY, true);
        AgentTool write = new StubTool("write", ToolSafetyLevel.WRITE, false);
        AgentTool bash = new StubTool("bash", ToolSafetyLevel.BASH, false);
        ToolRegistry registry = new ToolRegistry(List.of(read, write, bash));

        assertThat(registry.enabledTools(AgentRunOptions.safeDefaults())).containsExactly(read);
        assertThat(registry.enabledTools(new AgentRunOptions(
                true,
                false,
                6,
                12,
                Duration.ofSeconds(120)))).containsExactly(read, write);
        assertThat(registry.enabledTools(new AgentRunOptions(
                false,
                true,
                6,
                12,
                Duration.ofSeconds(120)))).containsExactly(read, bash);
    }

    @Test
    void rejectsDuplicateToolNames() {
        AgentTool first = new StubTool("read");
        AgentTool second = new StubTool("read");

        assertThatThrownBy(() -> new ToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    @Test
    void includesAdditionalMcpToolsOnlyWhenRunOptionsAllowThem() {
        AgentTool read = new StubTool("read");
        AgentTool mcp = new StubTool("mcp_demo_echo", ToolSafetyLevel.MCP, false);
        ToolRegistry registry = new ToolRegistry(List.of(read));

        assertThat(registry.enabledTools(AgentRunOptions.safeDefaults(), List.of(mcp)))
                .containsExactly(read);

        AgentRunOptions allowMcp = new AgentRunOptions(
                false,
                false,
                true,
                6,
                12,
                Duration.ofSeconds(120));
        assertThat(registry.enabledTools(allowMcp, List.of(mcp)))
                .containsExactly(read, mcp);
    }

    @Test
    void rejectsAdditionalToolNameCollision() {
        ToolRegistry registry = new ToolRegistry(List.of(new StubTool("read")));

        assertThatThrownBy(() -> registry.enabledTools(
                AgentRunOptions.safeDefaults(),
                List.of(new StubTool("read", ToolSafetyLevel.MCP, false))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate tool name");
    }

    @Test
    void rejectsUnknownToolName() {
        ToolRegistry registry = new ToolRegistry(List.of(new StubTool("read")));

        assertThatThrownBy(() -> registry.get("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown tool");
    }

    @Test
    void springRegistryIncludesEnabledReadOnlyBuiltInTools() {
        contextRunner
                .withPropertyValues("agent.workspace.root=.")
                .run(context -> {
                    assertThat(context).hasSingleBean(ToolRegistry.class);

                    ToolRegistry registry = context.getBean(ToolRegistry.class);

                    assertThat(registry.enabledTools())
                            .extracting(tool -> tool.spec().name())
                            .containsExactlyInAnyOrder("ls", "read", "find", "grep");
                    assertThat(registry.enabledTools())
                            .allSatisfy(tool -> {
                                assertThat(tool.spec().safetyLevel()).isEqualTo(ToolSafetyLevel.READ_ONLY);
                                assertThat(tool.spec().enabledByDefault()).isTrue();
                                assertThat(registry.get(tool.spec().name())).isSameAs(tool);
                            });
                });
    }

    @Test
    void springRegistryIncludesWriteToolsWhenAllowWriteIsTrue() {
        contextRunner
                .withPropertyValues("agent.workspace.root=.")
                .run(context -> {
                    ToolRegistry registry = context.getBean(ToolRegistry.class);
                    AgentRunOptions allowWrite = new AgentRunOptions(
                            true,
                            false,
                            6,
                            12,
                            Duration.ofSeconds(120));

                    assertThat(registry.enabledTools(allowWrite))
                            .extracting(tool -> tool.spec().name())
                            .containsExactlyInAnyOrder("ls", "read", "find", "grep", "write", "edit");
                    assertThat(registry.get("write").spec().safetyLevel()).isEqualTo(ToolSafetyLevel.WRITE);
                    assertThat(registry.get("edit").spec().safetyLevel()).isEqualTo(ToolSafetyLevel.WRITE);
                });
    }

    @Test
    void springRegistryIncludesBashToolWhenAllowBashIsTrue() {
        contextRunner
                .withPropertyValues("agent.workspace.root=.")
                .run(context -> {
                    ToolRegistry registry = context.getBean(ToolRegistry.class);
                    AgentRunOptions allowBash = new AgentRunOptions(
                            false,
                            true,
                            6,
                            12,
                            Duration.ofSeconds(120));

                    assertThat(registry.enabledTools(allowBash))
                            .extracting(tool -> tool.spec().name())
                            .containsExactlyInAnyOrder("ls", "read", "find", "grep", "bash");
                    assertThat(registry.get("bash").spec().safetyLevel()).isEqualTo(ToolSafetyLevel.BASH);
                });
    }

    private record StubTool(String name, ToolSafetyLevel safetyLevel, boolean enabledByDefault) implements AgentTool {

        private StubTool(String name) {
            this(name, ToolSafetyLevel.READ_ONLY, true);
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec(name, "stub", Map.of(), safetyLevel, enabledByDefault);
        }

        @Override
        public ToolResult execute(ToolArguments arguments) {
            return ToolResult.ok("ok");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ComponentScan(basePackageClasses = {ToolRegistry.class, SafePathResolver.class})
    @EnableConfigurationProperties({ToolProperties.class, WorkspaceProperties.class})
    static class ToolRegistrySpringConfig {
    }
}
