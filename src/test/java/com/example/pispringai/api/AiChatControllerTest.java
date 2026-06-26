package com.example.pispringai.api;

import com.example.pispringai.model.AiChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(AiChatController.class)
@Import(AiChatControllerTest.FakeAiChatServiceConfig.class)
class AiChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void chatReturnsModelContent() {
        webTestClient.post()
            .uri("/api/chat")
            .bodyValue(new ChatRequest("hello"))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.content").isEqualTo("fake response to hello");
    }

    @TestConfiguration
    static class FakeAiChatServiceConfig {

        @Bean
        AiChatService aiChatService() {
            return text -> Mono.just("fake response to " + text);
        }
    }
}
