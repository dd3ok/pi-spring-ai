package com.example.pispringai.tool.builtin;

import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolOutputLimiter;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.ToolSpec;
import com.example.pispringai.tool.policy.BashCommandPolicy;
import com.example.pispringai.tool.policy.ToolPolicyDecision;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.WorkspaceAccessDeniedException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;

@Component
public class BashTool implements AgentTool {

    private static final ToolSpec SPEC = new ToolSpec(
            "bash",
            "Run a shell command inside the workspace.",
            Map.of("command", "string"),
            ToolSafetyLevel.BASH,
            false);

    private static final boolean WINDOWS = File.separatorChar == '\\';
    private static final Executor STREAM_READER_EXECUTOR = command -> Thread.startVirtualThread(command);

    private final SafePathResolver pathResolver;
    private final BashCommandPolicy commandPolicy;
    private final ToolProperties toolProperties;

    public BashTool(
            SafePathResolver pathResolver,
            BashCommandPolicy commandPolicy,
            ToolProperties toolProperties) {
        this.pathResolver = pathResolver;
        this.commandPolicy = commandPolicy;
        this.toolProperties = toolProperties;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public ToolResult execute(ToolArguments arguments) {
        return execute(arguments, ToolExecutionContext.safeDefaults());
    }

    @Override
    public ToolResult execute(ToolArguments arguments, ToolExecutionContext context) {
        String command = arguments.requiredString("command");
        ToolPolicyDecision commandDecision = commandPolicy.validate(command);
        if (commandDecision.denied()) {
            return ToolResult.error("bash command denied: " + commandDecision.reason());
        }

        try {
            Path workspaceRoot = pathResolver.workspaceRoot(context.workspaceRoot());
            return runCommand(command, workspaceRoot);
        } catch (IOException | WorkspaceAccessDeniedException exception) {
            return ToolResult.error(exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return ToolResult.error("bash command interrupted");
        } catch (ExecutionException | TimeoutException exception) {
            return ToolResult.error("failed to read bash output: " + exceptionMessage(exception));
        }
    }

    private ToolResult runCommand(String command, Path workspaceRoot)
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        Process process = new ProcessBuilder(shellCommand(command))
                .directory(workspaceRoot.toFile())
                .start();
        process.getOutputStream().close();

        try (InputStream stdoutStream = process.getInputStream();
                InputStream stderrStream = process.getErrorStream()) {
            CompletableFuture<LimitedStream> stdout = readAsync(stdoutStream);
            CompletableFuture<LimitedStream> stderr = readAsync(stderrStream);
            Instant startedAt = Instant.now();
            boolean finished = process.waitFor(toolProperties.bashTimeoutSeconds(), TimeUnit.SECONDS);
            boolean timedOut = !finished;
            int exitCode = -1;
            if (timedOut) {
                destroyProcessTree(process);
                closeQuietly(stdoutStream);
                closeQuietly(stderrStream);
            } else {
                exitCode = process.exitValue();
            }

            LimitedStream stdoutText = stdout.get(2, TimeUnit.SECONDS);
            LimitedStream stderrText = stderr.get(2, TimeUnit.SECONDS);
            long durationMillis = Duration.between(startedAt, Instant.now()).toMillis();
            Map<String, Object> metadata = metadata(
                    command,
                    workspaceRoot,
                    exitCode,
                    timedOut,
                    durationMillis,
                    stdoutText,
                    stderrText);
            String text = resultText(exitCode, timedOut, stdoutText.text(), stderrText.text());
            return limitedResult(text, timedOut || exitCode != 0, metadata);
        }
    }

    private ToolResult limitedResult(String text, boolean error, Map<String, Object> metadata) {
        ToolOutputLimiter.LimitedOutput limited =
                ToolOutputLimiter.limit(text, toolProperties.maxToolOutputChars());
        Map<String, Object> resultMetadata = new LinkedHashMap<>(metadata);
        if (limited.truncated()) {
            resultMetadata.put("truncated", true);
        }
        return new ToolResult(limited.text(), error, resultMetadata);
    }

    private Map<String, Object> metadata(
            String command,
            Path workspaceRoot,
            int exitCode,
            boolean timedOut,
            long durationMillis,
            LimitedStream stdout,
            LimitedStream stderr) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("command", command);
        metadata.put("workingDirectory", workspaceRoot.toString());
        metadata.put("exitCode", exitCode);
        metadata.put("timedOut", timedOut);
        metadata.put("durationMillis", durationMillis);
        metadata.put("stdoutBytes", stdout.bytesRead());
        metadata.put("stderrBytes", stderr.bytesRead());
        metadata.put("stdoutTruncated", stdout.truncated());
        metadata.put("stderrTruncated", stderr.truncated());
        return metadata;
    }

    private static String resultText(int exitCode, boolean timedOut, String stdout, String stderr) {
        StringBuilder builder = new StringBuilder();
        if (timedOut) {
            builder.append("bash command timed out");
        } else {
            builder.append("bash exited with exit code ").append(exitCode);
        }
        if (!stdout.isBlank()) {
            builder.append(System.lineSeparator()).append("stdout:").append(System.lineSeparator()).append(stdout);
        }
        if (!stderr.isBlank()) {
            builder.append(System.lineSeparator()).append("stderr:").append(System.lineSeparator()).append(stderr);
        }
        return builder.toString();
    }

    private CompletableFuture<LimitedStream> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(
                () -> readLimited(stream, toolProperties.maxToolOutputChars()),
                STREAM_READER_EXECUTOR);
    }

    private static LimitedStream readLimited(InputStream stream, int limitBytes) {
        int limit = Math.max(1, limitBytes);
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] chunk = new byte[4096];
        long bytesRead = 0;
        boolean truncated = false;
        try {
            int read;
            while ((read = stream.read(chunk)) >= 0) {
                bytesRead += read;
                int remaining = limit - output.size();
                if (remaining > 0) {
                    int stored = Math.min(read, remaining);
                    output.write(chunk, 0, stored);
                    truncated = truncated || stored < read;
                } else {
                    truncated = true;
                }
            }
        } catch (IOException exception) {
            truncated = true;
        }
        return new LimitedStream(
                output.toString(StandardCharsets.UTF_8),
                bytesRead,
                truncated);
    }

    private static List<String> shellCommand(String command) {
        return WINDOWS
                ? List.of("cmd.exe", "/c", command)
                : List.of("/bin/sh", "-c", command);
    }

    private static void destroyProcessTree(Process process) throws InterruptedException {
        ProcessHandle handle = process.toHandle();
        handle.descendants()
                .toList()
                .forEach(ProcessHandle::destroyForcibly);
        handle.destroyForcibly();
        process.waitFor(2, TimeUnit.SECONDS);
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Closing on timeout is best-effort; partial output is still returned by the reader.
        }
    }

    private static String exceptionMessage(Exception exception) {
        Throwable cause = exception.getCause();
        if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
            return cause.getMessage();
        }
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }

    private record LimitedStream(String text, long bytesRead, boolean truncated) {
    }
}
