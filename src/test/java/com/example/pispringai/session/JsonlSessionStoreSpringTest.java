package com.example.pispringai.session;

import com.example.pispringai.agent.event.AgentStartedEvent;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.agent.message.UserMessage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "AGENT_MODEL_PROVIDER=google-genai",
        "AGENT_MCP_ENABLED=false",
        "spring.ai.google.genai.api-key=test-gemini-api-key",
        "agent.session.root=build/test-session-store-spring"
})
class JsonlSessionStoreSpringTest {

    private static final Path ALLOWED_TEST_ROOT = Path.of("build/test-session-store-spring")
            .toAbsolutePath()
            .normalize();

    @Autowired
    private SessionStore sessionStore;

    @Autowired
    private SessionProperties properties;

    @BeforeEach
    void cleanBefore() throws IOException {
        deleteRecursively(properties.root());
    }

    @AfterEach
    void cleanAfter() throws IOException {
        deleteRecursively(properties.root());
    }

    @Test
    void springWiredStoreRoundTripsMessagesAndEvents() throws Exception {
        Path workspace = properties.root().resolve("workspace");
        Files.createDirectories(workspace);

        AgentSession session = sessionStore.create(workspace);
        AgentMessage message = new UserMessage(
                "m_1",
                session.id(),
                null,
                Instant.parse("2026-06-29T00:00:00Z"),
                "hello");
        AgentStartedEvent event = new AgentStartedEvent(
                "e_1",
                session.id(),
                Instant.parse("2026-06-29T00:00:01Z"));

        sessionStore.appendMessage(session.id(), message);
        sessionStore.appendEvent(session.id(), event);

        assertThat(sessionStore.findSession(session.id())).contains(session);
        assertThat(sessionStore.loadMessages(session.id())).containsExactly(message);
        assertThat(sessionStore.loadEvents(session.id())).containsExactly(event);
    }

    private static void deleteRecursively(Path root) throws IOException {
        Path normalized = root.toAbsolutePath().normalize();
        if (!normalized.startsWith(ALLOWED_TEST_ROOT) || normalized.equals(ALLOWED_TEST_ROOT.getParent())) {
            throw new IllegalStateException("Refusing to delete unexpected test root: " + normalized);
        }
        if (Files.notExists(normalized)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(normalized)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
