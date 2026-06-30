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

class FindToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Files.createDirectories(workspace.resolve("src/main"));
        Files.writeString(workspace.resolve("src/main/App.java"), "class App {}");
        Files.writeString(workspace.resolve("src/main/AppTest.txt"), "not java");
        Files.writeString(workspace.resolve(".env"), "secret");
        Files.createDirectories(workspace.resolve(".git"));
        Files.writeString(workspace.resolve(".git/config"), "repo");
        Files.createDirectories(workspace.resolve("build"));
        Files.writeString(workspace.resolve("build/Generated.java"), "class Generated {}");
    }

    @Test
    void matchesFileNameGlobAndExcludesSecretAndIgnoredDirectories() {
        ToolResult result = newTool(100).execute(args("*.java", "."));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).contains("src/main/App.java");
        assertThat(result.text()).doesNotContain(".env", ".git", "build/Generated.java");
    }

    @Test
    void matchesWorkspaceRelativeGlob() {
        ToolResult result = newTool(100).execute(args("src/**/*.java", "."));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).contains("src/main/App.java");
        assertThat(result.text()).doesNotContain("AppTest.txt");
    }

    @Test
    void appliesMaxToolOutputChars() {
        ToolResult result = newTool(8).execute(args("*.java", "."));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).hasSizeLessThanOrEqualTo(8);
        assertThat(result.metadata()).containsEntry("truncated", true);
    }

    @Test
    void returnsErrorForTraversalOutsideWorkspace() throws Exception {
        Path outside = Files.createDirectory(tempDir.resolve("outside"));
        Files.writeString(outside.resolve("Outside.java"), "class Outside {}");

        ToolResult result = newTool(100).execute(args("*.java", "../outside"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("outside workspace");
    }

    private FindTool newTool(int maxOutputChars) {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(workspace, 200_000, 200, 100);
        return new FindTool(
                new SafePathResolver(workspaceProperties),
                new SecretFileGuard(),
                new ToolProperties(maxOutputChars));
    }

    private static ToolArguments args(String pattern, String path) {
        return new ToolArguments(Map.of("pattern", pattern, "path", path));
    }
}
