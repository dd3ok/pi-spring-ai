package com.example.pispringai.model;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AiChatService {

    Mono<String> chat(String text);
}
