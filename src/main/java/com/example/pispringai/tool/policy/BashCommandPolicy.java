package com.example.pispringai.tool.policy;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BashCommandPolicy {

    private static final Pattern RM_RECURSIVE_FORCE = Pattern.compile(
            "\\brm\\b(?=.*(?:\\s--recursive\\b|\\s-\\S*r\\S*\\b))(?=.*(?:\\s--force\\b|\\s-\\S*f\\S*\\b)).*");
    private static final Pattern GIT_CLEAN_FORCE_DIRECTORY =
            Pattern.compile("\\bgit\\b.*\\bclean\\b(?=.*(?:\\s--force\\b|\\s-\\S*f\\S*\\b))"
                    + "(?=.*(?:\\s--directory\\b|\\s-\\S*d\\S*\\b)).*");
    private static final Pattern GIT_RESET_HARD =
            Pattern.compile("\\bgit\\b.*\\breset\\b.*\\s--hard\\b");
    private static final String COMMAND_WRAPPERS =
            "(?:(?:sudo|doas)(?:\\s+-\\S+)*\\s+|env(?:\\s+(?:-\\S+|\\w+=\\S+))*\\s+)*";
    private static final String SHELL_INTERPRETER =
            "(?:\\S*/)?(?:sh|bash|dash|zsh|ksh)";
    private static final String POWERSHELL_INTERPRETER =
            "(?:\\S*/)?(?:iex|invoke-expression|powershell(?:\\.exe)?|pwsh(?:\\.exe)?)";
    private static final Pattern REMOTE_SCRIPT_PIPE =
            Pattern.compile("\\b(?:curl|wget)\\b.*\\|\\s*" + COMMAND_WRAPPERS + SHELL_INTERPRETER + "\\b");
    private static final Pattern REMOTE_POWERSHELL_PIPE =
            Pattern.compile("\\b(?:curl|wget|iwr|irm|invoke-webrequest|invoke-restmethod)\\b.*\\|\\s*"
                    + COMMAND_WRAPPERS + POWERSHELL_INTERPRETER + "\\b");
    private static final Pattern SHUTDOWN_OR_REBOOT =
            Pattern.compile("(?:^|[;&|]\\s*)(?:(?:sudo|doas|env)\\s+)*"
                    + "(?:shutdown(?:\\.exe)?|reboot|poweroff|halt|init\\s+[06])\\b");
    private static final Pattern POWERSHELL_REMOVE_ITEM_RECURSIVE =
            Pattern.compile("\\bremove-item\\b(?=.*\\s-(?:recurse|r)(?:\\b|[:=]))"
                    + "|\\b(?:powershell(?:\\.exe)?|pwsh(?:\\.exe)?)\\b.*"
                    + "\\b(?:ri|del|erase|rd|rmdir|rm)\\b(?=.*\\s-(?:recurse|r)(?:\\b|[:=]))");
    private static final Pattern WINDOWS_DELETE_RECURSIVE =
            Pattern.compile("\\b(?:del|erase|rd|rmdir)\\b.*\\s/s\\b");
    private static final Pattern COMMAND_TOKEN_SPLIT = Pattern.compile("[\\s|;&<>]+");
    private static final Pattern PATH_SEGMENT_SPLIT = Pattern.compile("[/\\\\=]+");

    private static final Set<String> SECRET_EXACT_NAMES = Set.of(
            "id_rsa",
            "id_dsa",
            "id_ecdsa",
            "id_ed25519",
            ".git",
            "application-prod.yml",
            "application-prod.yaml",
            "application-prod.properties");

    public ToolPolicyDecision validate(String command) {
        if (command == null || command.isBlank()) {
            return ToolPolicyDecision.deny("bash command must not be blank");
        }

        String normalized = normalize(command, false);
        String shellNormalized = normalize(command, true);
        if (isDestructive(normalized) || isDestructive(shellNormalized)) {
            return ToolPolicyDecision.deny("command is destructive");
        }
        if (mayExposeSecret(normalized) || mayExposeSecret(shellNormalized)) {
            return ToolPolicyDecision.deny("command references a secret path");
        }
        return ToolPolicyDecision.allow();
    }

    private static boolean isDestructive(String normalized) {
        return RM_RECURSIVE_FORCE.matcher(normalized).find()
                || GIT_RESET_HARD.matcher(normalized).find()
                || GIT_CLEAN_FORCE_DIRECTORY.matcher(normalized).find()
                || REMOTE_SCRIPT_PIPE.matcher(normalized).find()
                || REMOTE_POWERSHELL_PIPE.matcher(normalized).find()
                || SHUTDOWN_OR_REBOOT.matcher(normalized).find()
                || POWERSHELL_REMOVE_ITEM_RECURSIVE.matcher(normalized).find()
                || WINDOWS_DELETE_RECURSIVE.matcher(normalized).find();
    }

    private static boolean mayExposeSecret(String normalized) {
        return COMMAND_TOKEN_SPLIT.splitAsStream(normalized)
                .filter(token -> !token.isBlank())
                .anyMatch(BashCommandPolicy::isSecretPathToken);
    }

    private static boolean isSecretPathToken(String token) {
        return PATH_SEGMENT_SPLIT.splitAsStream(token)
                .filter(segment -> !segment.isBlank())
                .anyMatch(BashCommandPolicy::isSecretName);
    }

    private static boolean isSecretName(String name) {
        return name.equals(".env")
                || name.startsWith(".env.")
                || name.endsWith(".pem")
                || name.endsWith(".key")
                || name.startsWith("secrets.")
                || SECRET_EXACT_NAMES.contains(name);
    }

    private static String normalize(String command, boolean removeBackslashEscapes) {
        return dequote(command, removeBackslashEscapes)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static String dequote(String command, boolean removeBackslashEscapes) {
        StringBuilder builder = new StringBuilder(command.length());
        for (int index = 0; index < command.length(); index++) {
            char current = command.charAt(index);
            if (current == '\'' || current == '"') {
                continue;
            }
            if (removeBackslashEscapes && current == '\\') {
                if (index + 1 < command.length()) {
                    builder.append(command.charAt(++index));
                }
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }
}
