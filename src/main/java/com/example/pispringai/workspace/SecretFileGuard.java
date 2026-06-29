package com.example.pispringai.workspace;

import java.nio.file.Path;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class SecretFileGuard {

    public boolean isDenied(Path path) {
        if (path == null) {
            return true;
        }

        for (Path segment : path) {
            if (matchesDeniedName(segment.toString())) {
                return true;
            }
        }

        return false;
    }

    public void assertAllowed(Path path) {
        if (isDenied(path)) {
            throw new WorkspaceAccessDeniedException("secret file access denied: " + path);
        }
    }

    private static boolean matchesDeniedName(String fileName) {
        String lowerName = fileName.toLowerCase(Locale.ROOT);
        return lowerName.equals(".env")
                || lowerName.startsWith(".env.")
                || lowerName.endsWith(".pem")
                || lowerName.endsWith(".key")
                || lowerName.equals("id_rsa")
                || lowerName.equals("id_dsa")
                || lowerName.equals("id_ecdsa")
                || lowerName.equals("id_ed25519")
                || lowerName.equals(".git")
                || lowerName.equals("application-prod.yml")
                || lowerName.equals("application-prod.yaml")
                || lowerName.equals("application-prod.properties")
                || lowerName.startsWith("secrets.");
    }
}
