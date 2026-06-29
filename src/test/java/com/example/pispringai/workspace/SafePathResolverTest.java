package com.example.pispringai.workspace;

import java.io.IOException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafePathResolverTest {

    @TempDir
    Path tempDir;

    private Path workspace;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createDirectory(tempDir.resolve("workspace"));
    }

    @Test
    void resolvesExistingRelativePathInsideWorkspace() throws Exception {
        Path readme = Files.writeString(workspace.resolve("README.md"), "hello");

        assertThat(newResolver().resolveExisting("README.md")).isEqualTo(readme.toRealPath());
    }

    @Test
    void resolvesBothUnixAndWindowsSeparatorsInsideWorkspace() throws Exception {
        Path nested = Files.createDirectories(workspace.resolve("src").resolve("main"));
        Path javaFile = Files.writeString(nested.resolve("App.java"), "class App {}");
        SafePathResolver resolver = newResolver();

        assertThat(resolver.resolveExisting("src/main/App.java")).isEqualTo(javaFile.toRealPath());
        assertThat(resolver.resolveExisting("src\\main\\App.java")).isEqualTo(javaFile.toRealPath());
    }

    @Test
    void allowsAbsolutePathInsideWorkspace() throws Exception {
        Path readme = Files.writeString(workspace.resolve("README.md"), "hello");

        assertThat(newResolver().resolveExisting(readme.toString())).isEqualTo(readme.toRealPath());
    }

    @Test
    void deniesPathTraversalOutsideWorkspace() throws Exception {
        Files.writeString(tempDir.resolve("outside.txt"), "no");

        assertThatThrownBy(() -> newResolver().resolveExisting("../outside.txt"))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    void deniesAbsolutePathOutsideWorkspace() throws Exception {
        Path outside = Files.writeString(tempDir.resolve("outside.txt"), "no");

        assertThatThrownBy(() -> newResolver().resolveExisting(outside.toString()))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    void deniesExistingSymlinkThatEscapesWorkspace() throws Exception {
        Path outside = Files.writeString(tempDir.resolve("outside.txt"), "no");
        Path link = createSymlink(workspace.resolve("outside-link.txt"), outside);

        assertThatThrownBy(() -> newResolver().resolveExisting(link.getFileName().toString()))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    void resolvesFuturePathWhenExistingParentStaysInsideWorkspace() throws Exception {
        Path docs = Files.createDirectory(workspace.resolve("docs"));

        Path resolved = newResolver().resolveForFuturePath("docs/new-file.md");

        assertThat(resolved).isEqualTo(docs.toRealPath().resolve("new-file.md").normalize());
    }

    @Test
    void deniesFuturePathWhenExistingParentEscapesThroughSymlink() throws Exception {
        Path outsideDir = Files.createDirectory(tempDir.resolve("outside-dir"));
        Path link = createSymlink(workspace.resolve("escape"), outsideDir);

        assertThatThrownBy(() -> newResolver().resolveForFuturePath(link.getFileName() + "/new-file.md"))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("outside workspace");
    }

    @Test
    void deniesFuturePathWhenTargetAlreadyExists() throws Exception {
        Files.writeString(workspace.resolve("existing.txt"), "already here");

        assertThatThrownBy(() -> newResolver().resolveForFuturePath("existing.txt"))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deniesFuturePathWhenParentIsNotDirectory() throws Exception {
        Files.writeString(workspace.resolve("file-parent.txt"), "not a directory");

        assertThatThrownBy(() -> newResolver().resolveForFuturePath("file-parent.txt/new-file.md"))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("parent is not a directory");
    }

    @Test
    void deniesFuturePathWhenExistingTargetIsSymlink() throws Exception {
        Path outside = Files.writeString(tempDir.resolve("outside.txt"), "no");
        Path link = createSymlink(workspace.resolve("outside-link.txt"), outside);

        assertThatThrownBy(() -> newResolver().resolveForFuturePath(link.getFileName().toString()))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deniesAmbiguousWindowsDriveRelativePath() {
        assertThatThrownBy(() -> newResolver().resolveForFuturePath("C:relative.txt"))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    void deniesAmbiguousWindowsUncPath() {
        assertThatThrownBy(() -> newResolver().resolveForFuturePath("\\\\server\\share\\file.txt"))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("ambiguous");
    }

    @Test
    void deniesBlankPathFailClosed() {
        assertThatThrownBy(() -> newResolver().resolveExisting(" "))
                .isInstanceOf(WorkspaceAccessDeniedException.class)
                .hasMessageContaining("path must not be blank");
    }

    private SafePathResolver newResolver() {
        return new SafePathResolver(new WorkspaceProperties(workspace, 0, 0, 0));
    }

    private static Path createSymlink(Path link, Path target) throws IOException {
        try {
            return Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | FileSystemException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable: " + exception.getMessage());
            throw exception;
        }
    }
}
