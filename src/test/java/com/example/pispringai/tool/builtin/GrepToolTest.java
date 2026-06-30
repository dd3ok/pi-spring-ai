package com.example.pispringai.tool.builtin;

import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.SecretFileGuard;
import com.example.pispringai.workspace.WorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GrepToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("README.md"), "Spring AI\nAgentRuntime\n");
        Files.writeString(workspace.resolve("src/App.java"), "class App { String name = \"AgentRuntime\"; }\n");
        Files.writeString(workspace.resolve(".env"), "AgentRuntime=secret\n");
        Files.createDirectories(workspace.resolve(".git"));
        Files.writeString(workspace.resolve(".git/config"), "AgentRuntime");
        Files.write(workspace.resolve("binary.bin"), new byte[] {'A', 0, 'B'});
        Files.writeString(workspace.resolve("large.txt"), "large text without query");
    }

    @Test
    void findsLiteralMatchesAndExcludesSecretsAndIgnoredDirectories() {
        ToolResult result = newTool(200, 100, 200).execute(args("AgentRuntime", "."));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).contains("README.md:2:AgentRuntime", "src/App.java:1:");
        assertThat(result.text()).doesNotContain(".env", ".git");
        assertThat(result.metadata()).containsEntry("matches", 2);
    }

    @Test
    void skipsLargeAndBinaryFiles() {
        ToolResult result = newTool(5, 100, 200).execute(args("AgentRuntime", "."));

        assertThat(result.error()).isFalse();
        assertThat(result.metadata()).containsEntry("skippedLargeFiles", 3);
        assertThat(result.metadata()).containsEntry("skippedBinaryFiles", 1);
    }

    @Test
    void appliesMaxMatchesAndOutputLimit() {
        ToolResult result = newTool(200, 1, 10).execute(args("AgentRuntime", "."));

        assertThat(result.error()).isFalse();
        assertThat(result.metadata()).containsEntry("matches", 1);
        assertThat(result.metadata()).containsEntry("truncated", true);
        assertThat(result.text()).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void returnsErrorForTraversalOutsideWorkspace() throws Exception {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("outside.txt"), "AgentRuntime");

        ToolResult result = newTool(200, 100, 200).execute(args("AgentRuntime", "../outside"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("outside workspace");
    }

    private GrepTool newTool(long maxReadBytes, int maxGrepMatches, int maxOutputChars) {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(workspace, maxReadBytes, 200, maxGrepMatches);
        return new GrepTool(
                new SafePathResolver(workspaceProperties),
                new SecretFileGuard(),
                workspaceProperties,
                new ToolProperties(maxOutputChars));
    }

    private static ToolArguments args(String query, String path) {
        return new ToolArguments(Map.of("query", query, "path", path));
    }
}
