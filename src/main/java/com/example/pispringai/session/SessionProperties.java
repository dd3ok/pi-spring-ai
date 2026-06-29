package com.example.pispringai.session;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "agent.session")
public record SessionProperties(Path root) {

    private static final Path DEFAULT_ROOT = Path.of(".pi-spring-ai/sessions");

    public SessionProperties {
        root = (root == null ? DEFAULT_ROOT : root).toAbsolutePath().normalize();
    }
}
