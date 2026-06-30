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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LsTool implements AgentTool {

    private static final ToolSpec SPEC = new ToolSpec(
            "ls",
            "List entries in a workspace directory.",
            Map.of("path", "string"),
            ToolSafetyLevel.READ_ONLY,
            true);

    private final SafePathResolver pathResolver;
    private final SecretFileGuard secretFileGuard;
    private final WorkspaceProperties workspaceProperties;
    private final ToolProperties toolProperties;

    public LsTool(
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
            Path directory = pathResolver.resolveExisting(arguments.optionalString("path").orElse("."));
            if (!Files.isDirectory(directory)) {
                return ToolResult.error("path is not a directory: " + directory);
            }

            List<Path> entries;
            try (var stream = Files.list(directory)) {
                entries = stream
                        .filter(path -> !secretFileGuard.isDenied(pathResolver.workspaceRoot().relativize(path)))
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .toList();
            }

            int returnedEntries = Math.min(entries.size(), workspaceProperties.maxDirectoryEntries());
            String text = entries.stream()
                    .limit(returnedEntries)
                    .map(this::displayName)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("totalEntries", entries.size());
            metadata.put("returnedEntries", returnedEntries);
            if (returnedEntries < entries.size()) {
                metadata.put("truncated", true);
            }
            return ToolOutputLimiter.ok(text, metadata, toolProperties);
        } catch (IOException | IllegalArgumentException | WorkspaceAccessDeniedException exception) {
            return ToolResult.error(exception.getMessage());
        }
    }

    private String displayName(Path path) {
        String name = path.getFileName().toString();
        return Files.isDirectory(path) ? name + "/" : name;
    }
}
