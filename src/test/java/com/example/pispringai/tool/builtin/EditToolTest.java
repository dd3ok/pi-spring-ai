package com.example.pispringai.tool.builtin;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.policy.DefaultToolPolicy;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.SecretFileGuard;
import com.example.pispringai.workspace.WorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class EditToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
    }

    @Test
    void replacesOldTextWhenItMatchesExactlyOnce() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "hello old world");
        EditTool tool = newTool(100);

        ToolResult result = tool.execute(args("notes.txt", "old", "new"));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).contains("edited");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("hello new world");
        assertThat(result.metadata())
                .containsEntry("path", "notes.txt")
                .containsEntry("replacements", 1)
                .containsEntry("bytesWritten", 15L);
        assertThat(tool.spec().name()).isEqualTo("edit");
        assertThat(tool.spec().safetyLevel()).isEqualTo(ToolSafetyLevel.WRITE);
    }

    @Test
    void failsWhenOldTextDoesNotMatch() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "hello world");

        ToolResult result = newTool(100).execute(args("notes.txt", "missing", "new"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("not found");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("hello world");
    }

    @Test
    void failsWhenOldTextIsEmpty() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "hello world");

        ToolResult result = newTool(100).execute(args("notes.txt", "", "new"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("oldText must not be empty");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("hello world");
    }

    @Test
    void failsWhenOldTextMatchesMultipleTimes() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "old and old");

        ToolResult result = newTool(100).execute(args("notes.txt", "old", "new"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("multiple");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("old and old");
    }

    @Test
    void failsWhenTargetFileDoesNotExist() {
        ToolResult result = newTool(100).execute(args("missing.txt", "old", "new"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("failed to resolve");
    }

    @Test
    void rejectsPathOutsideWorkspace() throws Exception {
        Files.writeString(tempDir.resolve("outside.txt"), "old");

        ToolResult result = newTool(100).execute(args("../outside.txt", "old", "new"));

        assertThat(result.error()).isTrue();
        assertThat(Files.readString(tempDir.resolve("outside.txt"))).isEqualTo("old");
    }

    @Test
    void rejectsSecretFilePath() throws Exception {
        Files.writeString(workspace.resolve(".env"), "old");

        ToolResult result = newTool(100).execute(args(".env", "old", "new"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("denied");
        assertThat(Files.readString(workspace.resolve(".env"))).isEqualTo("old");
    }

    @Test
    void failsForLargeOrBinaryFiles() throws Exception {
        Files.writeString(workspace.resolve("large.txt"), "abcdef");
        Files.write(workspace.resolve("binary.bin"), new byte[] {'a', 0, 'b'});

        ToolResult large = newTool(5).execute(args("large.txt", "abc", "ABC"));
        ToolResult binary = newTool(100).execute(args("binary.bin", "a", "A"));

        assertThat(large.error()).isTrue();
        assertThat(large.text()).contains("too large");
        assertThat(Files.readString(workspace.resolve("large.txt"))).isEqualTo("abcdef");
        assertThat(binary.error()).isTrue();
        assertThat(binary.text()).contains("binary");
        assertThat(Files.readAllBytes(workspace.resolve("binary.bin"))).containsExactly('a', 0, 'b');
    }

    @Test
    void executionServiceDeniesBeforeExecutionWhenAllowWriteIsFalse() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "old");
        EditTool tool = newTool(100);
        ToolExecutionService service = service();

        ToolResult result = service.execute(
                tool,
                args("notes.txt", "old", "new"),
                new ToolExecutionContext(AgentRunOptions.safeDefaults(), workspace));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("allowWrite");
        assertThat(result.metadata()).containsEntry("policyDenied", true);
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("old");
    }

    @Test
    void executionServiceRunsWhenAllowWriteIsTrue() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "old");
        EditTool tool = newTool(100);
        ToolExecutionService service = service();

        ToolResult result = service.execute(
                tool,
                args("notes.txt", "old", "new"),
                new ToolExecutionContext(allowWriteOptions(), workspace));

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("new");
    }

    private EditTool newTool(long maxReadBytes) {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(workspace, maxReadBytes, 200, 100);
        return new EditTool(
                new SafePathResolver(workspaceProperties),
                new SecretFileGuard(),
                workspaceProperties);
    }

    private static ToolExecutionService service() {
        return new ToolExecutionService(new DefaultToolPolicy(new ToolProperties(200)));
    }

    private static AgentRunOptions allowWriteOptions() {
        return new AgentRunOptions(true, false, 6, 12, Duration.ofSeconds(120));
    }

    private static ToolArguments args(String path, String oldText, String newText) {
        return new ToolArguments(Map.of(
                "path", path,
                "oldText", oldText,
                "newText", newText));
    }
}
