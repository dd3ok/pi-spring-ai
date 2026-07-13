package com.example.pispringai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class McpPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void defaultsToEmptyAllowlist() {
        contextRunner.run(context -> {
            McpProperties properties = context.getBean(McpProperties.class);

            assertThat(properties.allowedTools()).isEmpty();
            assertThat(properties.allows("demo", "echo")).isFalse();
        });
    }

    @Test
    void bindsServerAndToolAllowlistEntries() {
        contextRunner
                .withPropertyValues("agent.mcp.allowed-tools=demo/echo,demo/read")
                .run(context -> {
                    McpProperties properties = context.getBean(McpProperties.class);

                    assertThat(properties.allowedTools()).containsExactlyInAnyOrder("demo/echo", "demo/read");
                    assertThat(properties.allows("demo", "echo")).isTrue();
                    assertThat(properties.allows("other", "echo")).isFalse();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(McpProperties.class)
    static class PropertiesConfig {
    }
}
