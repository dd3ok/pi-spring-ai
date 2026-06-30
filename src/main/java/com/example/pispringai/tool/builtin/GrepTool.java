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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GrepTool implements AgentTool {

    private static final ToolSpec SPEC = new ToolSpec(
            "grep",
            "Search UTF-8 text files in the workspace using literal substring matching.",
            Map.of("query", "string", "path", "string"),
            ToolSafetyLevel.READ_ONLY,
            true);

    private final SafePathResolver pathResolver;
    private final SecretFileGuard secretFileGuard;
    private final WorkspaceProperties workspaceProperties;
    private final ToolProperties toolProperties;

    public GrepTool(
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
            String query = arguments.requiredString("query");
            Path root = pathResolver.resolveExisting(arguments.optionalString("path").orElse("."));
            if (!Files.isDirectory(root)) {
                return ToolResult.error("path is not a directory: " + root);
            }

            SearchResult searchResult = search(root, query);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("matches", searchResult.matches());
            metadata.put("skippedLargeFiles", searchResult.skippedLargeFiles());
            metadata.put("skippedBinaryFiles", searchResult.skippedBinaryFiles());
            return ToolOutputLimiter.ok(String.join("\n", searchResult.lines()), metadata, toolProperties);
        } catch (IOException | IllegalArgumentException | WorkspaceAccessDeniedException exception) {
            return ToolResult.error(exception.getMessage());
        }
    }

    private SearchResult search(Path root, String query) throws IOException {
        List<String> lines = new ArrayList<>();
        int matches = 0;
        int skippedLargeFiles = 0;
        int skippedBinaryFiles = 0;

        for (Path file : WorkspaceWalk.regularFiles(root, secretFileGuard)) {
            long size = Files.size(file);
            if (size > workspaceProperties.maxReadBytes()) {
                skippedLargeFiles++;
                continue;
            }

            String text;
            try {
                text = TextFileReader.decodeUtf8Text(Files.readAllBytes(file));
            } catch (CharacterCodingException exception) {
                skippedBinaryFiles++;
                continue;
            }

            String relative = WorkspaceWalk.relativePath(pathResolver.workspaceRoot(), file);
            String[] fileLines = text.split("\\R", -1);
            for (int index = 0; index < fileLines.length && matches < workspaceProperties.maxGrepMatches(); index++) {
                String line = fileLines[index];
                if (line.contains(query)) {
                    lines.add(relative + ":" + (index + 1) + ":" + line);
                    matches++;
                }
            }
            if (matches >= workspaceProperties.maxGrepMatches()) {
                break;
            }
        }

        return new SearchResult(lines, matches, skippedLargeFiles, skippedBinaryFiles);
    }

    private record SearchResult(
            List<String> lines,
            int matches,
            int skippedLargeFiles,
            int skippedBinaryFiles
    ) {
    }
}
