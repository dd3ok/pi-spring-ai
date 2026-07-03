package com.example.pispringai.tool.builtin;

import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolExecutionContext;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.ToolSpec;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.SecretFileGuard;
import com.example.pispringai.workspace.WorkspaceAccessDeniedException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WriteTool implements AgentTool {

    private static final ToolSpec SPEC = new ToolSpec(
            "write",
            "Write a UTF-8 text file inside the workspace.",
            Map.of("path", "string", "content", "string", "overwrite", "boolean"),
            ToolSafetyLevel.WRITE,
            false);

    private final SafePathResolver pathResolver;
    private final SecretFileGuard secretFileGuard;

    public WriteTool(SafePathResolver pathResolver, SecretFileGuard secretFileGuard) {
        this.pathResolver = pathResolver;
        this.secretFileGuard = secretFileGuard;
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
        try {
            String pathArgument = arguments.requiredString("path");
            String content = requiredStringAllowingBlank(arguments, "content");
            boolean overwrite = optionalBoolean(arguments, "overwrite", false);
            Path workspaceRoot = pathResolver.workspaceRoot(context.workspaceRoot());
            ResolvedWriteTarget resolvedTarget = resolveTarget(workspaceRoot, pathArgument);
            Path target = resolvedTarget.path();
            secretFileGuard.assertAllowed(workspaceRoot.relativize(target));

            if (resolvedTarget.existing()) {
                if (!Files.isRegularFile(target)) {
                    return ToolResult.error("path is not a regular file: " + target);
                }
                if (!overwrite) {
                    return ToolResult.error("file already exists: " + target);
                }
            }

            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            if (resolvedTarget.existing()) {
                Files.writeString(
                        target,
                        content,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING);
            } else {
                Files.writeString(
                        target,
                        content,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW);
            }

            String relativePath = WorkspaceWalk.relativePath(workspaceRoot, target);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("path", relativePath);
            metadata.put("bytesWritten", (long) bytes.length);
            metadata.put("created", !resolvedTarget.existing());
            metadata.put("overwritten", resolvedTarget.existing());
            String action = resolvedTarget.existing() ? "overwrote" : "wrote";
            return ToolResult.ok(action + " file: " + relativePath, metadata);
        } catch (IOException | IllegalArgumentException | WorkspaceAccessDeniedException exception) {
            return ToolResult.error(exception.getMessage());
        }
    }

    private ResolvedWriteTarget resolveTarget(Path workspaceRoot, String pathArgument) {
        try {
            return new ResolvedWriteTarget(
                    pathResolver.resolveExisting(workspaceRoot, pathArgument),
                    true);
        } catch (WorkspaceAccessDeniedException exception) {
            return new ResolvedWriteTarget(
                    pathResolver.resolveForFuturePath(workspaceRoot, pathArgument),
                    false);
        }
    }

    private static String requiredStringAllowingBlank(ToolArguments arguments, String name) {
        Object value = arguments.values().get(name);
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException("missing required string argument: " + name);
        }
        return stringValue;
    }

    private static boolean optionalBoolean(ToolArguments arguments, String name, boolean defaultValue) {
        Object value = arguments.values().get(name);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw new IllegalArgumentException("argument must be a boolean: " + name);
    }

    private record ResolvedWriteTarget(Path path, boolean existing) {
    }
}
