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
import com.example.pispringai.workspace.WorkspaceProperties;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EditTool implements AgentTool {

    private static final ToolSpec SPEC = new ToolSpec(
            "edit",
            "Replace text in a UTF-8 text file inside the workspace.",
            Map.of("path", "string", "oldText", "string", "newText", "string"),
            ToolSafetyLevel.WRITE,
            false);

    private final SafePathResolver pathResolver;
    private final SecretFileGuard secretFileGuard;
    private final WorkspaceProperties workspaceProperties;

    public EditTool(
            SafePathResolver pathResolver,
            SecretFileGuard secretFileGuard,
            WorkspaceProperties workspaceProperties) {
        this.pathResolver = pathResolver;
        this.secretFileGuard = secretFileGuard;
        this.workspaceProperties = workspaceProperties;
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
            Path workspaceRoot = pathResolver.workspaceRoot(context.workspaceRoot());
            Path file = pathResolver.resolveExisting(workspaceRoot, arguments.requiredString("path"));
            secretFileGuard.assertAllowed(workspaceRoot.relativize(file));
            if (!Files.isRegularFile(file)) {
                return ToolResult.error("path is not a regular file: " + file);
            }

            long size = Files.size(file);
            if (size > workspaceProperties.maxReadBytes()) {
                return ToolResult.error("file is too large: " + size + " bytes");
            }

            String oldText = arguments.requiredString("oldText");
            String newText = requiredStringValue(arguments, "newText");
            String text = TextFileReader.decodeUtf8Text(Files.readAllBytes(file));
            int matches = countMatches(text, oldText);
            if (matches == 0) {
                return ToolResult.error("oldText not found");
            }
            if (matches > 1) {
                return ToolResult.error("oldText matched multiple times: " + matches);
            }

            String updatedText = text.replace(oldText, newText);
            byte[] bytes = updatedText.getBytes(StandardCharsets.UTF_8);
            Files.writeString(
                    file,
                    updatedText,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING);

            String relativePath = WorkspaceWalk.relativePath(workspaceRoot, file);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("path", relativePath);
            metadata.put("replacements", 1);
            metadata.put("bytesWritten", (long) bytes.length);
            return ToolResult.ok("edited file: " + relativePath, metadata);
        } catch (CharacterCodingException exception) {
            return ToolResult.error("file is binary or not valid UTF-8");
        } catch (IOException | IllegalArgumentException | WorkspaceAccessDeniedException exception) {
            return ToolResult.error(exception.getMessage());
        }
    }

    private static int countMatches(String text, String oldText) {
        int count = 0;
        int fromIndex = 0;
        int matchIndex;
        while ((matchIndex = text.indexOf(oldText, fromIndex)) >= 0) {
            count++;
            fromIndex = matchIndex + oldText.length();
        }
        return count;
    }

    private static String requiredStringValue(ToolArguments arguments, String name) {
        Object value = arguments.values().get(name);
        if (!(value instanceof String stringValue)) {
            throw new IllegalArgumentException("missing required string argument: " + name);
        }
        return stringValue;
    }
}
