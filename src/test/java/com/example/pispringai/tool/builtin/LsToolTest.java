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

class LsToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
    }

    @Test
    void listsDirectoryEntriesSortedAndExcludesSecrets() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "hello");
        Files.createDirectory(workspace.resolve("src"));
        Files.writeString(workspace.resolve(".env"), "secret");

        ToolResult result = newTool(20, 200).execute(args("."));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).contains("README.md", "src/");
        assertThat(result.text()).doesNotContain(".env");
    }

    @Test
    void appliesDirectoryEntryAndOutputLimits() throws Exception {
        Files.writeString(workspace.resolve("a.txt"), "a");
        Files.writeString(workspace.resolve("b.txt"), "b");
        Files.writeString(workspace.resolve("c.txt"), "c");

        ToolResult result = newTool(2, 5).execute(args("."));

        assertThat(result.error()).isFalse();
        assertThat(result.metadata()).containsEntry("truncated", true);
        assertThat(result.metadata()).containsEntry("returnedEntries", 2);
        assertThat(result.text()).hasSizeLessThanOrEqualTo(5);
    }

    @Test
    void returnsErrorWhenPathIsNotDirectory() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "hello");

        ToolResult result = newTool(20, 200).execute(args("README.md"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("directory");
    }

    @Test
    void returnsErrorForTraversalOutsideWorkspace() throws Exception {
        Files.createDirectory(tempDir.resolve("outside"));

        ToolResult result = newTool(20, 200).execute(args("../outside"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("outside workspace");
    }

    private LsTool newTool(int maxDirectoryEntries, int maxOutputChars) {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(workspace, 200_000, maxDirectoryEntries, 100);
        return new LsTool(
                new SafePathResolver(workspaceProperties),
                new SecretFileGuard(),
                workspaceProperties,
                new ToolProperties(maxOutputChars));
    }

    private static ToolArguments args(String path) {
        return new ToolArguments(Map.of("path", path));
    }
}
