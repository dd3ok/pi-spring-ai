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
        Files.createDirectories(workspace.resolve("build"));
        Files.writeString(workspace.resolve("build/Generated.txt"), "AgentRuntime");
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
    void keepsCountingSkippedFilesAfterMaxMatches() throws Exception {
        Path cappedWorkspace = Files.createDirectory(tempDir.resolve("capped-workspace"));
        Files.writeString(cappedWorkspace.resolve("00-match.txt"), "hit");
        Files.write(cappedWorkspace.resolve("01-binary.bin"), new byte[] {'A', 0, 'B'});
        Files.writeString(cappedWorkspace.resolve("02-large.txt"), "abcdef");
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(cappedWorkspace, 3, 200, 1);
        GrepTool tool = new GrepTool(
                new SafePathResolver(workspaceProperties),
                new SecretFileGuard(),
                workspaceProperties,
                new ToolProperties(200));

        ToolResult result = tool.execute(args("hit", "."));

        assertThat(result.error()).isFalse();
        assertThat(result.metadata()).containsEntry("matches", 1);
        assertThat(result.metadata()).containsEntry("skippedBinaryFiles", 1);
        assertThat(result.metadata()).containsEntry("skippedLargeFiles", 1);
    }

    @Test
    void returnsErrorForTraversalOutsideWorkspace() throws Exception {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("outside.txt"), "AgentRuntime");

        ToolResult result = newTool(200, 100, 200).execute(args("AgentRuntime", "../outside"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("outside workspace");
    }

    @Test
    void returnsErrorWhenSearchRootIsSecretOrIgnoredDirectory() {
        ToolResult gitResult = newTool(200, 100, 200).execute(args("AgentRuntime", ".git"));
        ToolResult buildResult = newTool(200, 100, 200).execute(args("AgentRuntime", "build"));

        assertThat(gitResult.error()).isTrue();
        assertThat(gitResult.text()).contains("denied");
        assertThat(gitResult.text()).doesNotContain("AgentRuntime");
        assertThat(buildResult.error()).isTrue();
        assertThat(buildResult.text()).contains("denied");
        assertThat(buildResult.text()).doesNotContain("AgentRuntime");
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
