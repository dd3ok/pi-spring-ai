package com.example.pispringai.tool;

import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.WorkspaceProperties;
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
    void rejectsDuplicateToolNames() {
        AgentTool first = new StubTool("read");
        AgentTool second = new StubTool("read");

        assertThatThrownBy(() -> new ToolRegistry(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
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

    private record StubTool(String name) implements AgentTool {
        @Override
        public ToolSpec spec() {
            return new ToolSpec(name, "stub", Map.of(), ToolSafetyLevel.READ_ONLY, true);
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
