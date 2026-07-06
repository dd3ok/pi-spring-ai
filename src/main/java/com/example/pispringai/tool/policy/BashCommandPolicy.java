package com.example.pispringai.tool.policy;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BashCommandPolicy {

    private static final Pattern RM_RECURSIVE_FORCE = Pattern.compile("\\brm\\s+-(?:\\S*r\\S*f|\\S*f\\S*r)\\b");
    private static final Pattern RM_SPLIT_RECURSIVE_FORCE = Pattern.compile("\\brm\\b(?=.*\\s-r\\b)(?=.*\\s-f\\b).*");
    private static final Pattern GIT_CLEAN_FORCE_DIRECTORY =
            Pattern.compile("\\bgit\\s+clean\\b(?=.*-(?:\\S*f\\S*d|\\S*d\\S*f)).*");
    private static final Pattern GIT_CLEAN_SPLIT_FORCE_DIRECTORY =
            Pattern.compile("\\bgit\\s+clean\\b(?=.*\\s-f\\b)(?=.*\\s-d\\b).*");
    private static final Pattern GIT_RESET_HARD =
            Pattern.compile("\\bgit\\s+reset\\b.*\\s--hard\\b");
    private static final Pattern REMOTE_SCRIPT_PIPE =
            Pattern.compile("\\b(?:curl|wget)\\b.*\\|\\s*(?:sh|bash)\\b");
    private static final Pattern SHUTDOWN_OR_REBOOT =
            Pattern.compile("(?:^|[;&|]\\s*)(?:shutdown(?:\\.exe)?|reboot|poweroff|halt|init\\s+[06])\\b");
    private static final Pattern WINDOWS_DELETE_RECURSIVE =
            Pattern.compile("\\b(?:del|erase|rd|rmdir)\\b.*\\s/s\\b");

    private static final List<String> SECRET_TOKENS = List.of(
            ".env",
            ".pem",
            ".key",
            "id_rsa",
            "id_dsa",
            "id_ecdsa",
            "id_ed25519",
            "secrets.",
            "application-prod",
            ".git");

    public ToolPolicyDecision validate(String command) {
        if (command == null || command.isBlank()) {
            return ToolPolicyDecision.deny("bash command must not be blank");
        }

        String normalized = normalize(command);
        if (isDestructive(normalized)) {
            return ToolPolicyDecision.deny("command is destructive");
        }
        if (mayExposeSecret(normalized)) {
            return ToolPolicyDecision.deny("command references a secret path");
        }
        return ToolPolicyDecision.allow();
    }

    private static boolean isDestructive(String normalized) {
        return RM_RECURSIVE_FORCE.matcher(normalized).find()
                || RM_SPLIT_RECURSIVE_FORCE.matcher(normalized).find()
                || GIT_RESET_HARD.matcher(normalized).find()
                || GIT_CLEAN_FORCE_DIRECTORY.matcher(normalized).find()
                || GIT_CLEAN_SPLIT_FORCE_DIRECTORY.matcher(normalized).find()
                || REMOTE_SCRIPT_PIPE.matcher(normalized).find()
                || SHUTDOWN_OR_REBOOT.matcher(normalized).find()
                || normalized.contains("remove-item") && normalized.contains("-recurse")
                || WINDOWS_DELETE_RECURSIVE.matcher(normalized).find();
    }

    private static boolean mayExposeSecret(String normalized) {
        return SECRET_TOKENS.stream().anyMatch(normalized::contains);
    }

    private static String normalize(String command) {
        return command
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
