package com.example.pispringai.tool.builtin;

import com.example.pispringai.workspace.SecretFileGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceWalkTest {

    @TempDir
    Path tempDir;

    @Test
    void ignoresBuildDirectoryOnlyInsideWalkRoot() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("build").resolve("workspace"));
        Path file = Files.createDirectories(workspace.resolve("src")).resolve("App.java");
        Files.writeString(file, "class App {}");
        Files.createDirectories(workspace.resolve("build"));
        Files.writeString(workspace.resolve("build/Generated.java"), "class Generated {}");

        var files = WorkspaceWalk.regularFiles(workspace, new SecretFileGuard());

        assertThat(files).contains(file);
        assertThat(files).doesNotContain(workspace.resolve("build/Generated.java"));
    }
}
