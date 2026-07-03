package com.example.pispringai.workspace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class SafePathResolver {

    private static final Pattern WINDOWS_DRIVE_ABSOLUTE = Pattern.compile("^[A-Za-z]:[\\\\/].*");
    private static final Pattern WINDOWS_DRIVE_RELATIVE = Pattern.compile("^[A-Za-z]:(?![\\\\/]).*");
    private static final boolean WINDOWS_HOST = File.separatorChar == '\\';

    private final Path workspaceRoot;

    public SafePathResolver(WorkspaceProperties properties) {
        if (properties == null) {
            throw new WorkspaceAccessDeniedException("workspace properties must not be null");
        }
        this.workspaceRoot = realPath(properties.root(), "workspace root");
    }

    public Path workspaceRoot() {
        return workspaceRoot;
    }

    public Path workspaceRoot(Path overrideRoot) {
        return overrideRoot == null ? workspaceRoot : realPath(overrideRoot, "workspace root");
    }

    public Path resolveExisting(String userPath) {
        return resolveExisting(workspaceRoot, userPath);
    }

    public Path resolveExisting(Path workspaceRoot, String userPath) {
        Path root = workspaceRoot(workspaceRoot);
        Path realPath = realPath(candidatePath(root, userPath), "workspace path");
        assertInsideWorkspace(root, realPath);
        return realPath;
    }

    public Path resolveForFuturePath(String userPath) {
        return resolveForFuturePath(workspaceRoot, userPath);
    }

    public Path resolveForFuturePath(Path workspaceRoot, String userPath) {
        Path root = workspaceRoot(workspaceRoot);
        Path candidate = candidatePath(root, userPath);
        Path parent = candidate.getParent();
        Path fileName = candidate.getFileName();
        if (parent == null || fileName == null) {
            throw new WorkspaceAccessDeniedException("workspace path must have a parent: " + userPath);
        }

        Path realParent = realPath(parent, "workspace path parent");
        assertInsideWorkspace(root, realParent);
        if (!readAttributes(realParent, "workspace path parent").isDirectory()) {
            throw new WorkspaceAccessDeniedException("future workspace path parent is not a directory: " + realParent);
        }
        assertFuturePathDoesNotExist(candidate);
        return realParent.resolve(fileName).normalize();
    }

    private Path candidatePath(Path root, String userPath) {
        if (userPath == null || userPath.isBlank()) {
            throw new WorkspaceAccessDeniedException("path must not be blank");
        }

        try {
            rejectAmbiguousWindowsPath(userPath);
            Path input = Path.of(userPath.replace('\\', File.separatorChar));
            return (input.isAbsolute() ? input : root.resolve(input)).normalize();
        } catch (InvalidPathException exception) {
            throw new WorkspaceAccessDeniedException("invalid workspace path: " + userPath, exception);
        }
    }

    private void rejectAmbiguousWindowsPath(String userPath) {
        String normalized = userPath.replace('/', '\\');
        if (WINDOWS_DRIVE_RELATIVE.matcher(normalized).matches()) {
            throw new WorkspaceAccessDeniedException("ambiguous Windows drive-relative path: " + userPath);
        }
        if (WINDOWS_DRIVE_ABSOLUTE.matcher(normalized).matches() && !WINDOWS_HOST) {
            throw new WorkspaceAccessDeniedException("ambiguous Windows absolute path: " + userPath);
        }
        if (normalized.startsWith("\\\\") && !workspaceRoot.toString().startsWith("\\\\")) {
            throw new WorkspaceAccessDeniedException("ambiguous Windows UNC path: " + userPath);
        }
    }

    private Path realPath(Path path, String label) {
        try {
            return path.toRealPath().normalize();
        } catch (IOException | SecurityException exception) {
            throw new WorkspaceAccessDeniedException("failed to resolve " + label + ": " + path, exception);
        }
    }

    private BasicFileAttributes readAttributes(Path path, String label) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException | SecurityException exception) {
            throw new WorkspaceAccessDeniedException("failed to read " + label + " attributes: " + path, exception);
        }
    }

    private void assertFuturePathDoesNotExist(Path candidate) {
        try {
            Files.readAttributes(candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            throw new WorkspaceAccessDeniedException("future workspace path already exists: " + candidate);
        } catch (NoSuchFileException exception) {
            return;
        } catch (IOException | SecurityException exception) {
            throw new WorkspaceAccessDeniedException(
                    "failed to determine future workspace path existence: " + candidate,
                    exception);
        }
    }

    private static void assertInsideWorkspace(Path workspaceRoot, Path path) {
        if (!path.startsWith(workspaceRoot)) {
            throw new WorkspaceAccessDeniedException("path is outside workspace: " + path);
        }
    }
}
