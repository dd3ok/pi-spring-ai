package com.example.pispringai.session;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

@ConfigurationProperties(prefix = "agent.session")
public record SessionProperties(Path root) {

    public SessionProperties {
        root = requireNonNull(root, "root").toAbsolutePath().normalize();
    }
}
