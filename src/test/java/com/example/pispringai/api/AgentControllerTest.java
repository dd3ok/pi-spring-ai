package com.example.pispringai.api;

import com.example.pispringai.agent.AgentPromptCommand;
import com.example.pispringai.agent.AgentRuntime;
import com.example.pispringai.agent.event.AgentCompletedEvent;
import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.agent.event.AgentStartedEvent;
import com.example.pispringai.agent.event.ToolCallFinishedEvent;
import com.example.pispringai.agent.event.ToolCallStartedEvent;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.agent.message.AssistantMessage;
import com.example.pispringai.agent.message.AssistantStopReason;
import com.example.pispringai.agent.message.ToolResultMessage;
import com.example.pispringai.agent.message.UserMessage;
import com.example.pispringai.session.AgentSession;
import com.example.pispringai.session.SessionStore;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

@WebFluxTest(AgentController.class)
@Import(AgentControllerTest.FakeAgentConfig.class)
class AgentControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private RecordingRuntime runtime;

    @Test
    void createSessionReturnsSessionIdAndWorkspaceRoot() {
        webTestClient.post()
                .uri("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"workspaceRoot\":\"build/sample-workspace\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.sessionId").isEqualTo("s_1")
                .jsonPath("$.workspaceRoot").isEqualTo(Path.of("build/sample-workspace").toAbsolutePath().normalize().toString());
    }

    @Test
    void createSessionReturnsBadRequestForInvalidWorkspaceRoot() {
        webTestClient.post()
                .uri("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"workspaceRoot\":\"bad\\u0000path\"}")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void streamMessageReturnsAgentEventsAsSseAndPassesRunOptions() {
        webTestClient.post()
                .uri("/api/sessions/s_existing/messages/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"hello\",\"allowWrite\":true,\"allowBash\":false}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .consumeWith(result -> {
                    String body = result.getResponseBody();
                    assertThat(body).contains("event:agent_event");
                    assertThat(body).contains("id:e_1");
                    assertThat(body).contains("agent_started");
                    assertThat(body).contains("agent_completed");
                });

        assertThat(runtime.lastCommand).isNotNull();
        assertThat(runtime.lastCommand.sessionId()).isEqualTo("s_existing");
        assertThat(runtime.lastCommand.userText()).isEqualTo("hello");
        assertThat(runtime.lastCommand.options().allowWrite()).isTrue();
        assertThat(runtime.lastCommand.options().allowBash()).isFalse();
    }

    @Test
    void streamMessageDefaultsMissingToolOptionsToSafeFalse() {
        webTestClient.post()
                .uri("/api/sessions/s_existing/messages/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"hello\"}")
                .exchange()
                .expectStatus().isOk();

        assertThat(runtime.lastCommand.options().allowWrite()).isFalse();
        assertThat(runtime.lastCommand.options().allowBash()).isFalse();
    }

    @Test
    void streamMessageReturnsNotFoundForMissingSession() {
        webTestClient.post()
                .uri("/api/sessions/s_missing/messages/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"text\":\"hello\"}")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void getSessionReturnsSessionMessagesAndEventsForReplay() {
        webTestClient.get()
                .uri("/api/sessions/s_existing")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.session.id").isEqualTo("s_existing")
                .jsonPath("$.session.workspaceRoot").isEqualTo(Path.of("build/sample-workspace").toAbsolutePath().normalize().toString())
                .jsonPath("$.messages[0].kind").isEqualTo("user")
                .jsonPath("$.messages[0].text").isEqualTo("hello")
                .jsonPath("$.messages[1].kind").isEqualTo("tool_result")
                .jsonPath("$.messages[1].toolName").isEqualTo("read")
                .jsonPath("$.messages[1].resultText").isEqualTo("README.md")
                .jsonPath("$.messages[1].error").isEqualTo(false)
                .jsonPath("$.messages[2].kind").isEqualTo("assistant")
                .jsonPath("$.messages[2].text").isEqualTo("done")
                .jsonPath("$.events[0].type").isEqualTo("agent_started")
                .jsonPath("$.events[1].type").isEqualTo("tool_call_started")
                .jsonPath("$.events[1].callId").isEqualTo("tc_1")
                .jsonPath("$.events[1].tool").isEqualTo("read")
                .jsonPath("$.events[2].type").isEqualTo("tool_call_finished")
                .jsonPath("$.events[2].callId").isEqualTo("tc_1")
                .jsonPath("$.events[2].error").isEqualTo(false)
                .jsonPath("$.events[3].type").isEqualTo("agent_completed");
    }

    @Test
    void getSessionReturnsNotFoundForMissingSession() {
        webTestClient.get()
                .uri("/api/sessions/s_missing")
                .exchange()
                .expectStatus().isNotFound();
    }

    @TestConfiguration
    static class FakeAgentConfig {

        @Bean
        SessionStore sessionStore() {
            return new FakeSessionStore();
        }

        @Bean
        RecordingRuntime agentRuntime() {
            return new RecordingRuntime();
        }
    }

    private static final class RecordingRuntime implements AgentRuntime {

        private AgentPromptCommand lastCommand;

        @Override
        public Flux<AgentEvent> prompt(AgentPromptCommand command) {
            this.lastCommand = command;
            return Flux.just(
                    new AgentStartedEvent("e_1", command.sessionId(), Instant.parse("2026-06-29T00:00:00Z")),
                    new AgentCompletedEvent("e_2", command.sessionId(), Instant.parse("2026-06-29T00:00:01Z")));
        }
    }

    private static final class FakeSessionStore implements SessionStore {

        private final List<AgentSession> sessions = new ArrayList<>();

        private FakeSessionStore() {
            sessions.add(new AgentSession(
                    "s_existing",
                    Path.of("build/sample-workspace"),
                    Instant.parse("2026-06-29T00:00:00Z"),
                    Instant.parse("2026-06-29T00:00:00Z"),
                    null));
        }

        @Override
        public AgentSession create(Path workspaceRoot) {
            AgentSession session = new AgentSession(
                    "s_1",
                    workspaceRoot,
                    Instant.parse("2026-06-29T00:00:00Z"),
                    Instant.parse("2026-06-29T00:00:00Z"),
                    null);
            sessions.add(session);
            return session;
        }

        @Override
        public Optional<AgentSession> findSession(String sessionId) {
            return sessions.stream()
                    .filter(session -> session.id().equals(sessionId))
                    .findFirst();
        }

        @Override
        public void appendMessage(String sessionId, AgentMessage message) {
        }

        @Override
        public void appendEvent(String sessionId, AgentEvent event) {
        }

        @Override
        public List<AgentMessage> loadMessages(String sessionId) {
            if ("s_existing".equals(sessionId)) {
                return List.of(
                        new UserMessage(
                                "m_1",
                                sessionId,
                                null,
                                Instant.parse("2026-06-29T00:00:00Z"),
                                "hello"),
                        new ToolResultMessage(
                                "m_2",
                                sessionId,
                                "m_1",
                                Instant.parse("2026-06-29T00:00:01Z"),
                                "tc_1",
                                "read",
                                "README.md",
                                false),
                        new AssistantMessage(
                                "m_3",
                                sessionId,
                                "m_2",
                                Instant.parse("2026-06-29T00:00:02Z"),
                                "done",
                                AssistantStopReason.STOP));
            }
            return List.of();
        }

        @Override
        public List<AgentEvent> loadEvents(String sessionId) {
            if ("s_existing".equals(sessionId)) {
                return List.of(
                        new AgentStartedEvent("e_1", sessionId, Instant.parse("2026-06-29T00:00:00Z")),
                        new ToolCallStartedEvent("e_2", sessionId, Instant.parse("2026-06-29T00:00:01Z"), "tc_1", "read"),
                        new ToolCallFinishedEvent("e_3", sessionId, Instant.parse("2026-06-29T00:00:02Z"), "tc_1", "read", false),
                        new AgentCompletedEvent("e_4", sessionId, Instant.parse("2026-06-29T00:00:03Z")));
            }
            return List.of();
        }
    }
}
