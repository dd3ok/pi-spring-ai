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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class FindTool implements AgentTool {

    private static final ToolSpec SPEC = new ToolSpec(
            "find",
            "Find files in the workspace using a glob pattern.",
            Map.of("pattern", "string", "path", "string"),
            ToolSafetyLevel.READ_ONLY,
            true);

    private final SafePathResolver pathResolver;
    private final SecretFileGuard secretFileGuard;
    private final ToolProperties toolProperties;

    public FindTool(
            SafePathResolver pathResolver,
            SecretFileGuard secretFileGuard,
            ToolProperties toolProperties) {
        this.pathResolver = pathResolver;
        this.secretFileGuard = secretFileGuard;
        this.toolProperties = toolProperties;
    }

    @Override
    public ToolSpec spec() {
        return SPEC;
    }

    @Override
    public ToolResult execute(ToolArguments arguments) {
        try {
            String pattern = arguments.requiredString("pattern");
            Path root = pathResolver.resolveExisting(arguments.optionalString("path").orElse("."));
            if (!Files.isDirectory(root)) {
                return ToolResult.error("path is not a directory: " + root);
            }

            GlobMatcher matcher = new GlobMatcher(pattern);
            var matches = WorkspaceWalk.regularFiles(root, secretFileGuard).stream()
                    .map(path -> WorkspaceWalk.relativePath(pathResolver.workspaceRoot(), path))
                    .filter(relative -> matcher.matches(relative))
                    .sorted()
                    .toList();

            String text = String.join("\n", matches);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("matches", matches.size());
            return ToolOutputLimiter.ok(text, metadata, toolProperties);
        } catch (IOException | IllegalArgumentException | WorkspaceAccessDeniedException exception) {
            return ToolResult.error(exception.getMessage());
        }
    }

    private static final class GlobMatcher {
        private final Pattern pattern;
        private final boolean pathPattern;

        private GlobMatcher(String glob) {
            this.pattern = Pattern.compile(toRegex(glob));
            this.pathPattern = glob.contains("/") || glob.contains("\\");
        }

        private boolean matches(String relativePath) {
            String normalized = relativePath.replace('\\', '/');
            String target = pathPattern ? normalized : fileName(normalized);
            return pattern.matcher(target).matches();
        }

        private static String fileName(String path) {
            int index = path.lastIndexOf('/');
            return index >= 0 ? path.substring(index + 1) : path;
        }

        private static String toRegex(String glob) {
            String normalized = glob.replace('\\', '/');
            StringBuilder regex = new StringBuilder("^");
            for (int i = 0; i < normalized.length(); i++) {
                char current = normalized.charAt(i);
                if (current == '*') {
                    if (i + 1 < normalized.length() && normalized.charAt(i + 1) == '*') {
                        regex.append(".*");
                        i++;
                    } else {
                        regex.append("[^/]*");
                    }
                } else if (current == '?') {
                    regex.append("[^/]");
                } else {
                    if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                        regex.append('\\');
                    }
                    regex.append(current);
                }
            }
            return regex.append('$').toString();
        }
    }
}
