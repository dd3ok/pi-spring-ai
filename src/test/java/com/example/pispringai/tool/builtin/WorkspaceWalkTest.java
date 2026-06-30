package com.example.pispringai.tool.builtin;

import com.example.pispringai.workspace.SecretFileGuard;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        List<Path> files = new ArrayList<>();

        WorkspaceWalk.walkRegularFiles(workspace, new SecretFileGuard(), visited -> {
            files.add(visited);
            return true;
        });

        assertThat(files).contains(file);
        assertThat(files).doesNotContain(workspace.resolve("build/Generated.java"));
    }

    @Test
    void stopsWalkingWhenVisitorReturnsFalse() throws Exception {
        Path workspace = Files.createDirectory(tempDir.resolve("workspace"));
        Files.writeString(workspace.resolve("a.txt"), "a");
        Files.writeString(workspace.resolve("b.txt"), "b");
        List<Path> visited = new ArrayList<>();

        WorkspaceWalk.walkRegularFiles(workspace, new SecretFileGuard(), file -> {
            visited.add(file);
            return false;
        });

        assertThat(visited).hasSize(1);
    }
}
