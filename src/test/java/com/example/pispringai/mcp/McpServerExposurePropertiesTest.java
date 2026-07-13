package com.example.pispringai.mcp;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerExposurePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void defaultsToReadOnlyTools() {
        contextRunner.run(context -> {
            McpServerExposureProperties properties = context.getBean(McpServerExposureProperties.class);

            assertThat(properties.exposedTools()).containsExactly("ls", "read", "find", "grep");
            assertThat(properties.allowWrite()).isFalse();
            assertThat(properties.allowBash()).isFalse();
        });
    }

    @Test
    void bindsExplicitExposureAndPermissionFlags() {
        contextRunner
                .withPropertyValues(
                        "agent.mcp.server.exposed-tools=read,write,bash",
                        "agent.mcp.server.allow-write=true",
                        "agent.mcp.server.allow-bash=true")
                .run(context -> {
                    McpServerExposureProperties properties = context.getBean(McpServerExposureProperties.class);

                    assertThat(properties.exposedTools()).containsExactly("read", "write", "bash");
                    assertThat(properties.allowWrite()).isTrue();
                    assertThat(properties.allowBash()).isTrue();
                });
    }

    @Test
    void rejectsBlankAndDuplicateToolNames() {
        assertThatThrownBy(() -> new McpServerExposureProperties(List.of("read", " "), false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> new McpServerExposureProperties(List.of("read", " read "), false, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("duplicate MCP server exposed tool: read");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(McpServerExposureProperties.class)
    static class PropertiesConfig {
    }
}
