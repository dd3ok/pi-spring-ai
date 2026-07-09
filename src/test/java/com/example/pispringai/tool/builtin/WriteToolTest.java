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
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class WriteToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
    }

    @Test
    void createsNewFile() throws Exception {
        WriteTool tool = newTool();

        ToolResult result = tool.execute(args("notes.txt", "hello", false));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).contains("wrote");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("hello");
        assertThat(result.metadata())
                .containsEntry("path", "notes.txt")
                .containsEntry("bytesWritten", 5L)
                .containsEntry("created", true)
                .containsEntry("overwritten", false);
        assertThat(tool.spec().name()).isEqualTo("write");
        assertThat(tool.spec().safetyLevel()).isEqualTo(ToolSafetyLevel.WRITE);
    }

    @Test
    void failsWhenFileExistsAndOverwriteIsFalse() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "original");

        ToolResult result = newTool().execute(args("notes.txt", "changed", false));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("already exists");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("original");
    }

    @Test
    void defaultsOverwriteToFalseWhenOmitted() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "original");

        ToolResult result = newTool().execute(argsWithoutOverwrite("notes.txt", "changed"));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("already exists");
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("original");
    }

    @Test
    void overwritesExistingFileWhenOverwriteIsTrue() throws Exception {
        Files.writeString(workspace.resolve("notes.txt"), "original");

        ToolResult result = newTool().execute(args("notes.txt", "changed", true));

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("changed");
        assertThat(result.metadata())
                .containsEntry("path", "notes.txt")
                .containsEntry("bytesWritten", 7L)
                .containsEntry("created", false)
                .containsEntry("overwritten", true);
    }

    @Test
    void rejectsPathOutsideWorkspace() {
        ToolResult result = newTool().execute(args("../outside.txt", "outside", false));

        assertThat(result.error()).isTrue();
        assertThat(Files.exists(tempDir.resolve("outside.txt"))).isFalse();
    }

    @Test
    void rejectsSecretFilePath() {
        ToolResult result = newTool().execute(args(".env", "SECRET=yes", false));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("denied");
        assertThat(Files.exists(workspace.resolve(".env"))).isFalse();
    }

    @Test
    void rejectsNestedSecretFilePaths() throws Exception {
        Path config = Files.createDirectories(workspace.resolve("config"));
        Path keys = Files.createDirectories(workspace.resolve("keys"));
        Path resources = Files.createDirectories(workspace.resolve("src/main/resources"));

        ToolResult envLocal = newTool().execute(args("config/.env.local", "SECRET=yes", false));
        ToolResult privateKey = newTool().execute(args("keys/id_rsa", "secret", false));
        ToolResult prodConfig = newTool().execute(args(
                "src/main/resources/application-prod.yml",
                "secret: yes",
                false));

        assertThat(envLocal.error()).isTrue();
        assertThat(privateKey.error()).isTrue();
        assertThat(prodConfig.error()).isTrue();
        assertThat(envLocal.text()).contains("denied");
        assertThat(privateKey.text()).contains("denied");
        assertThat(prodConfig.text()).contains("denied");
        assertThat(Files.exists(config.resolve(".env.local"))).isFalse();
        assertThat(Files.exists(keys.resolve("id_rsa"))).isFalse();
        assertThat(Files.exists(resources.resolve("application-prod.yml"))).isFalse();
    }

    @Test
    void failsWhenParentDirectoryIsMissing() {
        ToolResult result = newTool().execute(args("missing/notes.txt", "hello", false));

        assertThat(result.error()).isTrue();
        assertThat(Files.exists(workspace.resolve("missing"))).isFalse();
    }

    @Test
    void executionServiceDeniesBeforeExecutionWhenAllowWriteIsFalse() {
        WriteTool tool = newTool();
        ToolExecutionService service = service();

        ToolResult result = service.execute(
                tool,
                args("notes.txt", "hello", false),
                new ToolExecutionContext(AgentRunOptions.safeDefaults(), workspace));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("allowWrite");
        assertThat(result.metadata()).containsEntry("policyDenied", true);
        assertThat(Files.exists(workspace.resolve("notes.txt"))).isFalse();
    }

    @Test
    void executionServiceRunsWhenAllowWriteIsTrue() throws Exception {
        WriteTool tool = newTool();
        ToolExecutionService service = service();

        ToolResult result = service.execute(
                tool,
                args("notes.txt", "hello", false),
                new ToolExecutionContext(allowWriteOptions(), workspace));

        assertThat(result.error()).isFalse();
        assertThat(Files.readString(workspace.resolve("notes.txt"))).isEqualTo("hello");
    }

    private WriteTool newTool() {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(workspace, 100, 200, 100);
        return new WriteTool(
                new SafePathResolver(workspaceProperties),
                new SecretFileGuard());
    }

    private static ToolExecutionService service() {
        return new ToolExecutionService(new DefaultToolPolicy(new ToolProperties(200, 30)));
    }

    private static AgentRunOptions allowWriteOptions() {
        return new AgentRunOptions(true, false, 6, 12, Duration.ofSeconds(120));
    }

    private static ToolArguments args(String path, String content, boolean overwrite) {
        Map<String, Object> values = new HashMap<>();
        values.put("path", path);
        values.put("content", content);
        values.put("overwrite", overwrite);
        return new ToolArguments(values);
    }

    private static ToolArguments argsWithoutOverwrite(String path, String content) {
        return new ToolArguments(Map.of(
                "path", path,
                "content", content));
    }
}
