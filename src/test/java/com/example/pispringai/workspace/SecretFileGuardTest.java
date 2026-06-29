package com.example.pispringai.workspace;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretFileGuardTest {

    private final SecretFileGuard guard = new SecretFileGuard();

    @Test
    void deniesDefaultSecretFileNames() {
        List.of(
                ".env",
                ".env.local",
                "prod.pem",
                "deploy.key",
                "id_rsa",
                "id_ed25519",
                "application-prod.yml",
                "application-prod.yaml",
                "secrets.json")
                .forEach(name -> assertThat(guard.isDenied(Path.of(name))).as(name).isTrue());
    }

    @Test
    void deniesSecretFileNamesInNestedPaths() {
        assertThat(guard.isDenied(Path.of("config", ".env.local"))).isTrue();
        assertThat(guard.isDenied(Path.of("keys", "id_rsa"))).isTrue();
    }

    @Test
    void allowsNormalFileNamesThatOnlyResembleSecrets() {
        List.of(
                "README.md",
                "application.yml",
                "id_rsa.pub",
                "secrets",
                "secrets-guide.md",
                "notes.key.md",
                "pem-notes.txt")
                .forEach(name -> assertThat(guard.isDenied(Path.of(name))).as(name).isFalse());
    }

    @Test
    void assertAllowedThrowsAccessDeniedExceptionForSecrets() {
        assertThatThrownBy(() -> guard.assertAllowed(Path.of(".env")))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("secret");
    }
}
