package com.example.pispringai.api;

public record ChatRequest(String text) {

    public ChatRequest {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be null or blank");
        }
    }
}
