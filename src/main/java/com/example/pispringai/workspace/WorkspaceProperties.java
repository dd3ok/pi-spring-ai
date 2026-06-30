package com.example.pispringai.workspace;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.workspace")
public record WorkspaceProperties(
        Path root,
        long maxReadBytes,
        int maxDirectoryEntries,
        int maxGrepMatches) {

    private static final Path DEFAULT_ROOT = Path.of(".");
    private static final long DEFAULT_MAX_READ_BYTES = 200_000;
    private static final int DEFAULT_MAX_DIRECTORY_ENTRIES = 200;
    private static final int DEFAULT_MAX_GREP_MATCHES = 100;

    public WorkspaceProperties {
        root = (root == null ? DEFAULT_ROOT : root).toAbsolutePath().normalize();
        maxReadBytes = maxReadBytes > 0 ? maxReadBytes : DEFAULT_MAX_READ_BYTES;
        maxDirectoryEntries = maxDirectoryEntries > 0 ? maxDirectoryEntries : DEFAULT_MAX_DIRECTORY_ENTRIES;
        maxGrepMatches = maxGrepMatches > 0 ? maxGrepMatches : DEFAULT_MAX_GREP_MATCHES;
    }
}
