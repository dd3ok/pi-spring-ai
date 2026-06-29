package com.example.pispringai.session;

public class SessionStoreException extends RuntimeException {

    public SessionStoreException(String message) {
        super(message);
    }

    public SessionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}
