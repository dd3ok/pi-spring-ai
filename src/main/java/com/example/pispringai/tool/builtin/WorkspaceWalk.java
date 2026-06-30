package com.example.pispringai.tool.builtin;

import com.example.pispringai.workspace.SecretFileGuard;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WorkspaceWalk {

    private WorkspaceWalk() {
    }

    static List<Path> regularFiles(Path root, SecretFileGuard secretFileGuard) throws IOException {
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && shouldSkip(root, dir, secretFileGuard)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && !shouldSkip(root, file, secretFileGuard)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files;
    }

    static boolean shouldSkip(Path root, Path path, SecretFileGuard secretFileGuard) {
        Path relativePath = root.relativize(path);
        return secretFileGuard.isDenied(relativePath) || hasIgnoredSegment(relativePath);
    }

    static String relativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
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
}
