package com.example.pispringai.tool.builtin;

import com.example.pispringai.workspace.SecretFileGuard;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Locale;

final class WorkspaceWalk {

    private WorkspaceWalk() {
    }

    static void walkRegularFiles(Path root, SecretFileGuard secretFileGuard, RegularFileVisitor visitor)
            throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && shouldSkip(root, dir, secretFileGuard)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile() && !shouldSkip(root, file, secretFileGuard)) {
                    if (!visitor.visit(file)) {
                        return FileVisitResult.TERMINATE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    static boolean shouldSkip(Path root, Path path, SecretFileGuard secretFileGuard) {
        Path relativePath = root.relativize(path);
        return shouldSkipRelative(relativePath, secretFileGuard);
    }

    static boolean shouldSkipRoot(Path workspaceRoot, Path root, SecretFileGuard secretFileGuard) {
        Path relativePath = workspaceRoot.relativize(root);
        return !relativePath.toString().isEmpty() && shouldSkipRelative(relativePath, secretFileGuard);
    }

    static String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static boolean shouldSkipRelative(Path relativePath, SecretFileGuard secretFileGuard) {
        return secretFileGuard.isDenied(relativePath) || hasIgnoredSegment(relativePath);
    }

    private static boolean hasIgnoredSegment(Path path) {
        for (Path segment : path) {
            String name = segment.toString().toLowerCase(Locale.ROOT);
            if (name.equals(".git") || name.equals("build") || name.equals(".gradle")) {
                return true;
            }
        }
        return false;
    }

    @FunctionalInterface
    interface RegularFileVisitor {
        boolean visit(Path file) throws IOException;
    }
}
