package com.example.pispringai.tool.builtin;

import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolExecutionService;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.policy.BashCommandPolicy;
import com.example.pispringai.tool.policy.DefaultToolPolicy;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.WorkspaceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class BashToolTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
    }

    @Test
    void runsSafeCommandInWorkspace() {
        BashTool tool = newTool(2_000, 30);

        ToolResult result = tool.execute(
                args(printWorkingDirectoryCommand()),
                new ToolExecutionContext(allowBashOptions(), workspace));

        assertThat(result.error()).isFalse();
        assertThat(result.text()).contains("exit code 0");
        assertThat(result.text()).contains(workspace.toAbsolutePath().normalize().toString());
        assertThat(result.metadata())
                .containsEntry("command", printWorkingDirectoryCommand())
                .containsEntry("workingDirectory", workspace.toAbsolutePath().normalize().toString())
                .containsEntry("exitCode", 0)
                .containsEntry("timedOut", false);
        assertThat(tool.spec().name()).isEqualTo("bash");
        assertThat(tool.spec().safetyLevel()).isEqualTo(ToolSafetyLevel.BASH);
    }

    @Test
    void rejectsDestructiveCommandBeforeExecution() throws Exception {
        Files.writeString(workspace.resolve("keep.txt"), "safe");
        BashTool tool = newTool(2_000, 30);

        ToolResult result = tool.execute(
                args("rm -rf keep.txt"),
                new ToolExecutionContext(allowBashOptions(), workspace));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("denied");
        assertThat(Files.readString(workspace.resolve("keep.txt"))).isEqualTo("safe");
    }

    @Test
    void timesOutLongRunningCommand() {
        BashTool tool = newTool(2_000, 1);

        ToolResult result = tool.execute(
                args(sleepCommand(5)),
                new ToolExecutionContext(allowBashOptions(), workspace));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("timed out");
        assertThat(result.metadata())
                .containsEntry("timedOut", true)
                .containsEntry("exitCode", -1);
    }

    @Test
    void returnsPartialOutputWhenTimedOutCommandIsDestroyed() {
        BashTool tool = newTool(2_000, 1);

        ToolResult result = tool.execute(
                args(partialOutputThenSleepCommand("started", 5)),
                new ToolExecutionContext(allowBashOptions(), workspace));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("timed out", "started");
        assertThat(result.metadata()).containsEntry("timedOut", true);
        assertThat((Long) result.metadata().get("stdoutBytes")).isGreaterThanOrEqualTo(7L);
    }

    @Test
    void limitsCommandOutput() {
        BashTool tool = newTool(4, 30);

        ToolResult result = tool.execute(
                args(printStdoutCommand("abcdef")),
                new ToolExecutionContext(allowBashOptions(), workspace));

        assertThat(result.metadata())
                .containsEntry("stdoutTruncated", true)
                .containsEntry("truncated", true);
        assertThat(result.text()).doesNotContain("abcdef");
    }

    @Test
    void executionServiceDeniesBeforeExecutionWhenAllowBashIsFalse() {
        BashTool tool = newTool(2_000, 30);
        ToolExecutionService service = service();

        ToolResult result = service.execute(
                tool,
                args(createFileCommand("marker.txt")),
                new ToolExecutionContext(AgentRunOptions.safeDefaults(), workspace));

        assertThat(result.error()).isTrue();
        assertThat(result.text()).contains("allowBash");
        assertThat(result.metadata()).containsEntry("policyDenied", true);
        assertThat(Files.exists(workspace.resolve("marker.txt"))).isFalse();
    }

    @Test
    void executionServiceRunsWhenAllowBashIsTrue() {
        BashTool tool = newTool(2_000, 30);
        ToolExecutionService service = service();

        ToolResult result = service.execute(
                tool,
                args(createFileCommand("marker.txt")),
                new ToolExecutionContext(allowBashOptions(), workspace));

        assertThat(result.error()).isFalse();
        assertThat(Files.exists(workspace.resolve("marker.txt"))).isTrue();
    }

    private BashTool newTool(int maxOutputChars, int timeoutSeconds) {
        WorkspaceProperties workspaceProperties = new WorkspaceProperties(workspace, 100, 200, 100);
        return new BashTool(
                new SafePathResolver(workspaceProperties),
                new BashCommandPolicy(),
                new ToolProperties(maxOutputChars, timeoutSeconds));
    }

    private static ToolExecutionService service() {
        return new ToolExecutionService(new DefaultToolPolicy(new ToolProperties(200, 30)));
    }

    private static AgentRunOptions allowBashOptions() {
        return new AgentRunOptions(false, true, 6, 12, Duration.ofSeconds(120));
    }

    private static ToolArguments args(String command) {
        return new ToolArguments(Map.of("command", command));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static String printWorkingDirectoryCommand() {
        return isWindows() ? "cd" : "pwd";
    }

    private static String sleepCommand(int seconds) {
        return isWindows()
                ? "powershell -NoProfile -Command \"Start-Sleep -Seconds " + seconds + "\""
                : "sleep " + seconds;
    }

    private static String printStdoutCommand(String text) {
        return isWindows()
                ? "powershell -NoProfile -Command \"[Console]::Out.Write('" + text + "')\""
                : "printf '" + text + "'";
    }

    private static String partialOutputThenSleepCommand(String text, int seconds) {
        return isWindows()
                ? "powershell -NoProfile -Command \"[Console]::Out.Write('" + text
                        + "'); [Console]::Out.Flush(); Start-Sleep -Seconds " + seconds + "\""
                : "printf '" + text + "'; sleep " + seconds;
    }

    private static String createFileCommand(String name) {
        return isWindows() ? "echo ok > " + name : "printf ok > " + name;
    }
}
