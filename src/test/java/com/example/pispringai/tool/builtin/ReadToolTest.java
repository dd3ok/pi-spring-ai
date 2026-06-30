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

class ReadToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
    }

    @Test
    void readsUtf8TextFileInsideWorkspace() throws Exception {
        Files.writeString(workspace.resolve("README.md"), "hello\nworld");

        ToolResult result = newTool(100, 200).execute(args("README.md"));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).isEqualTo("hello\nworld");
        assertThat(result.metadata()).containsEntry("bytes", 11L);
    }

    @Test
    void returnsErrorForTraversalAndSecretPaths() throws Exception {
        Files.writeString(tempDir.resolve("outside.txt"), "outside");
        Files.writeString(workspace.resolve(".env"), "SECRET=yes");
        ReadTool tool = newTool(100, 200);

        assertThat(tool.execute(args("../outside.txt")).error()).isTrue();
        ToolResult secret = tool.execute(args(".env"));

        assertThat(secret.error()).isTrue();
        assertThat(secret.text()).contains("denied");
    }

    @Test
    void returnsErrorForLargeOrBinaryFiles() throws Exception {
        Files.writeString(workspace.resolve("large.txt"), "abcdef");
        Files.write(workspace.resolve("binary.bin"), new byte[] {'a', 0, 'b'});
        ReadTool tool = newTool(5, 200);

        assertThat(tool.execute(args("large.txt")).error()).isTrue();
        ToolResult binary = tool.execute(args("binary.bin"));

        assertThat(binary.error()).isTrue();
        assertThat(binary.text()).contains("binary");
    }

    @Test
    void truncatesOutputToMaxToolOutputChars() throws Exception {
        Files.writeString(workspace.resolve("long.txt"), "0123456789");

        ToolResult result = newTool(100, 4).execute(args("long.txt"));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).isEqualTo("0123");
        assertThat(result.metadata()).containsEntry("truncated", true);
    }

    private ReadTool newTool(long maxReadBytes, int maxOutputChars) {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(workspace, maxReadBytes, 200, 100);
        return new ReadTool(
                new SafePathResolver(workspaceProperties),
                new SecretFileGuard(),
                workspaceProperties,
                new ToolProperties(maxOutputChars));
    }

    private static ToolArguments args(String path) {
        return new ToolArguments(Map.of("path", path));
    }
}
