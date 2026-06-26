package com.example.pispringai.model;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class SpringAiChatService implements AiChatService {

    private final ChatClient chatClient;

    public SpringAiChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public Mono<String> chat(String text) {
        return Mono.fromCallable(() -> chatClient.prompt()
                .user(text)
                .call()
                .content())
            .subscribeOn(Schedulers.boundedElastic());
    }
}
