package com.example.pispringai.workspace;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspacePropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(WorkspacePropertiesBindingConfig.class);

    @TempDir
    Path tempDir;

    @Test
    void usesDefaultWorkspaceValuesWhenNotConfigured() {
        WorkspaceProperties properties = new WorkspaceProperties(null, 0, 0, 0);

        assertThat(properties.root()).isEqualTo(Path.of(".").toAbsolutePath().normalize());
        assertThat(properties.maxReadBytes()).isEqualTo(200_000);
        assertThat(properties.maxDirectoryEntries()).isEqualTo(200);
        assertThat(properties.maxGrepMatches()).isEqualTo(100);
    }

    @Test
    void normalizesConfiguredWorkspaceRootToAbsolutePath() {
        Path configured = tempDir.resolve("workspace").resolve("..").resolve("workspace");

        WorkspaceProperties properties = new WorkspaceProperties(configured, 123, 4, 5);

        assertThat(properties.root()).isEqualTo(tempDir.resolve("workspace").toAbsolutePath().normalize());
        assertThat(properties.maxReadBytes()).isEqualTo(123);
        assertThat(properties.maxDirectoryEntries()).isEqualTo(4);
        assertThat(properties.maxGrepMatches()).isEqualTo(5);
    }

    @Test
    void bindsConfiguredWorkspaceProperties() {
        Path configured = tempDir.resolve("workspace");
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "agent.workspace.root", configured.toString(),
                "agent.workspace.max-read-bytes", "123",
                "agent.workspace.max-directory-entries", "4",
                "agent.workspace.max-grep-matches", "5"));

        WorkspaceProperties properties = new Binder(source)
                .bind("agent.workspace", WorkspaceProperties.class)
                .get();

        assertThat(properties.root()).isEqualTo(configured.toAbsolutePath().normalize());
        assertThat(properties.maxReadBytes()).isEqualTo(123);
        assertThat(properties.maxDirectoryEntries()).isEqualTo(4);
        assertThat(properties.maxGrepMatches()).isEqualTo(5);
    }

    @Test
    void springContextBindsConfiguredWorkspaceProperties() {
        Path configured = tempDir.resolve("workspace");

        contextRunner
                .withPropertyValues(
                        "agent.workspace.root=" + configured,
                        "agent.workspace.max-read-bytes=123",
                        "agent.workspace.max-directory-entries=4",
                        "agent.workspace.max-grep-matches=5")
                .run(context -> {
                    assertThat(context).hasSingleBean(WorkspaceProperties.class);
                    WorkspaceProperties properties = context.getBean(WorkspaceProperties.class);
                    assertThat(properties.root()).isEqualTo(configured.toAbsolutePath().normalize());
                    assertThat(properties.maxReadBytes()).isEqualTo(123);
                    assertThat(properties.maxDirectoryEntries()).isEqualTo(4);
                    assertThat(properties.maxGrepMatches()).isEqualTo(5);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(WorkspaceProperties.class)
    static class WorkspacePropertiesBindingConfig {
    }
}
