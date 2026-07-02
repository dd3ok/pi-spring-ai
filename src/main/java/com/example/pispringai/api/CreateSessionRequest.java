package com.example.pispringai.api;

public record CreateSessionRequest(String workspaceRoot) {

    public CreateSessionRequest {
        if (workspaceRoot == null || workspaceRoot.isBlank()) {
            throw new IllegalArgumentException("workspaceRoot must not be blank");
        }
    }
}
