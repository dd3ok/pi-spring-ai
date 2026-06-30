package com.example.pispringai.tool.builtin;

import com.example.pispringai.tool.AgentTool;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolOutputLimiter;
import com.example.pispringai.tool.ToolProperties;
import com.example.pispringai.tool.ToolResult;
import com.example.pispringai.tool.ToolSafetyLevel;
import com.example.pispringai.tool.ToolSpec;
import com.example.pispringai.workspace.SafePathResolver;
import com.example.pispringai.workspace.SecretFileGuard;
import com.example.pispringai.workspace.WorkspaceAccessDeniedException;
import com.example.pispringai.workspace.WorkspaceProperties;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ReadTool implements AgentTool {

    private static final ToolSpec SPEC = new ToolSpec(
            "read",
            "Read a UTF-8 text file inside the workspace.",
            Map.of("path", "string"),
            ToolSafetyLevel.READ_ONLY,
            true);

    private final SafePathResolver pathResolver;
    private final SecretFileGuard secretFileGuard;
    private final WorkspaceProperties workspaceProperties;
    private final ToolProperties toolProperties;

    public ReadTool(
            SafePathResolver pathResolver,
            SecretFileGuard secretFileGuard,
            WorkspaceProperties workspaceProperties,
            ToolProperties toolProperties) {
        this.pathResolver = pathResolver;
        this.secretFileGuard = secretFileGuard;
        this.workspaceProperties = workspaceProperties;
        this.toolProperties = toolProperties;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public ToolResult execute(ToolArguments arguments) {
        try {
            Path file = pathResolver.resolveExisting(arguments.requiredString("path"));
            secretFileGuard.assertAllowed(pathResolver.workspaceRoot().relativize(file));
            if (!Files.isRegularFile(file)) {
                return ToolResult.error("path is not a regular file: " + file);
            }

            long size = Files.size(file);
            if (size > workspaceProperties.maxReadBytes()) {
                return ToolResult.error("file is too large: " + size + " bytes");
            }

            String text = TextFileReader.decodeUtf8Text(Files.readAllBytes(file));
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("bytes", size);
            return ToolOutputLimiter.ok(text, metadata, toolProperties);
        } catch (CharacterCodingException exception) {
            return ToolResult.error("file is binary or not valid UTF-8");
        } catch (IOException | IllegalArgumentException | WorkspaceAccessDeniedException exception) {
            return ToolResult.error(exception.getMessage());
        }
    }
}
