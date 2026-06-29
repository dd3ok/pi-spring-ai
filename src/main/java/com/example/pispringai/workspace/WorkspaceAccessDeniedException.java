package com.example.pispringai.workspace;

public class WorkspaceAccessDeniedException extends RuntimeException {

    public WorkspaceAccessDeniedException(String message) {
        super(message);
    }

    public WorkspaceAccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
