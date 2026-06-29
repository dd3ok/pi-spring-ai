package com.example.pispringai.session;

import java.nio.file.Path;
import java.time.Instant;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlank;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonBlankWhenProvided;
import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

public record AgentSession(
        String id,
        Path workspaceRoot,
        Instant createdAt,
        Instant updatedAt,
        String title
) {

    public AgentSession {
        id = requireNonBlank(id, "id");
        workspaceRoot = requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        createdAt = requireNonNull(createdAt, "createdAt");
        updatedAt = requireNonNull(updatedAt, "updatedAt");
        title = requireNonBlankWhenProvided(title, "title");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("updatedAt must not be before createdAt");
        }
    }
}
