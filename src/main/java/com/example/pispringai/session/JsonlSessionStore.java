package com.example.pispringai.session;

import com.example.pispringai.agent.IdGenerator;
import com.example.pispringai.agent.event.AgentCompletedEvent;
import com.example.pispringai.agent.event.AgentErrorEvent;
import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.agent.event.AgentStartedEvent;
import com.example.pispringai.agent.event.AssistantCompletedEvent;
import com.example.pispringai.agent.event.AssistantDeltaEvent;
import com.example.pispringai.agent.event.ToolCallDeniedEvent;
import com.example.pispringai.agent.event.ToolCallFinishedEvent;
import com.example.pispringai.agent.event.ToolCallStartedEvent;
import com.example.pispringai.agent.event.UserMessageAppendedEvent;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.agent.message.AssistantMessage;
import com.example.pispringai.agent.message.ToolResultMessage;
import com.example.pispringai.agent.message.UserMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;

import static com.example.pispringai.agent.AgentDomainValidation.requireNonNull;

@Repository
public class JsonlSessionStore implements SessionStore {

    private static final String SESSION_JSON = "session.json";
    private static final String MESSAGES_JSONL = "messages.jsonl";
    private static final String EVENTS_JSONL = "events.jsonl";

    private final Path root;
    private final ObjectMapper objectMapper;

    public JsonlSessionStore(
            SessionProperties properties,
            @Qualifier("sessionObjectMapper") ObjectMapper objectMapper) {
        this.root = requireNonNull(properties, "properties").root();
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public AgentSession create(Path workspaceRoot) {
        Instant now = Instant.now();
        AgentSession session = new AgentSession(IdGenerator.sessionId(), workspaceRoot, now, now, null);
        Path sessionDir = sessionDirectory(session.id());
        try {
            Files.createDirectories(sessionDir);
            writeSessionMetadata(session);
            createFileIfMissing(sessionDir.resolve(MESSAGES_JSONL));
            createFileIfMissing(sessionDir.resolve(EVENTS_JSONL));
            return session;
        } catch (IOException ex) {
            throw new SessionStoreException("Failed to create session " + session.id(), ex);
        }
    }

    @Override
    public Optional<AgentSession> findSession(String sessionId) {
        Path sessionDir = sessionDirectory(sessionId);
        if (Files.notExists(sessionDir)) {
            return Optional.empty();
        }
        return Optional.of(readSessionMetadata(sessionId));
    }

    @Override
    public void appendMessage(String sessionId, AgentMessage message) {
        requireSessionDirectory(sessionId);
        ensureRecordSession(sessionId, requireRecord(message, "message"), "message");
        appendJsonLine(sessionDirectory(sessionId).resolve(MESSAGES_JSONL), message, "message", sessionId);
    }

    @Override
    public void appendEvent(String sessionId, AgentEvent event) {
        requireSessionDirectory(sessionId);
        ensureRecordSession(sessionId, requireRecord(event, "event"), "event");
        appendJsonLine(sessionDirectory(sessionId).resolve(EVENTS_JSONL), event, "event", sessionId);
    }

    @Override
    public List<AgentMessage> loadMessages(String sessionId) {
        requireSessionDirectory(sessionId);
        Path messages = sessionDirectory(sessionId).resolve(MESSAGES_JSONL);
        try (BufferedReader reader = Files.newBufferedReader(messages, StandardCharsets.UTF_8)) {
            List<AgentMessage> loaded = new ArrayList<>();
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                JsonNode root = readJsonObject(line, "message", lineNumber);
                String kind = textDiscriminator(root, "kind", "message kind", lineNumber);
                loaded.add(toMessage(new StoredMessage(kind, root), lineNumber));
            }
            return loaded;
        } catch (IOException ex) {
            throw new SessionStoreException("Failed to read messages for session " + sessionId, ex);
        }
    }

    @Override
    public List<AgentEvent> loadEvents(String sessionId) {
        requireSessionDirectory(sessionId);
        Path events = sessionDirectory(sessionId).resolve(EVENTS_JSONL);
        try (BufferedReader reader = Files.newBufferedReader(events, StandardCharsets.UTF_8)) {
            List<AgentEvent> loaded = new ArrayList<>();
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                JsonNode root = readJsonObject(line, "event", lineNumber);
                String type = textDiscriminator(root, "type", "event type", lineNumber);
                loaded.add(toEvent(new StoredEvent(type, root), lineNumber));
            }
            return loaded;
        } catch (IOException ex) {
            throw new SessionStoreException("Failed to read events for session " + sessionId, ex);
        }
    }

    private Path sessionDirectory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()
                || sessionId.contains("/")
                || sessionId.contains("\\")
                || ".".equals(sessionId)
                || "..".equals(sessionId)
                || Path.of(sessionId).isAbsolute()) {
            throw new SessionStoreException("Invalid sessionId: " + sessionId);
        }
        Path resolved = root.resolve(sessionId).normalize();
        if (!resolved.startsWith(root)) {
            throw new SessionStoreException("Invalid sessionId: " + sessionId);
        }
        return resolved;
    }

    private Path requireSessionDirectory(String sessionId) {
        Path sessionDir = sessionDirectory(sessionId);
        if (!Files.isDirectory(sessionDir)) {
            throw new SessionStoreException("Session not found: " + sessionId);
        }
        return sessionDir;
    }

    private void writeSessionMetadata(AgentSession session) throws IOException {
        StoredSession stored = StoredSession.from(session);
        Path sessionJson = sessionDirectory(session.id()).resolve(SESSION_JSON);
        try (OutputStream output = Files.newOutputStream(
                sessionJson,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            objectMapper.writeValue(output, stored);
        }
    }

    private AgentSession readSessionMetadata(String sessionId) {
        Path sessionJson = requireSessionDirectory(sessionId).resolve(SESSION_JSON);
        if (!Files.isRegularFile(sessionJson)) {
            throw new SessionStoreException("Session metadata not found: " + sessionId);
        }
        try (InputStream input = Files.newInputStream(sessionJson)) {
            StoredSession stored = objectMapper.readValue(input, StoredSession.class);
            return stored.toAgentSession();
        } catch (IOException | RuntimeException ex) {
            throw new SessionStoreException("Failed to read session metadata for session " + sessionId, ex);
        }
    }

    private void appendJsonLine(Path path, Object value, String recordType, String sessionId) {
        try {
            String line = objectMapper.writeValueAsString(value);
            Files.writeString(
                    path,
                    line + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException ex) {
            throw new SessionStoreException("Failed to append " + recordType + " for session " + sessionId, ex);
        }
    }

    private static <T> T requireRecord(T value, String recordType) {
        if (value == null) {
            throw new SessionStoreException(recordType + " must not be null");
        }
        return value;
    }

    private static void ensureRecordSession(String sessionId, AgentMessage message, String recordType) {
        if (!sessionId.equals(message.sessionId())) {
            throw new SessionStoreException(recordType + " does not belong to session " + sessionId);
        }
    }

    private static void ensureRecordSession(String sessionId, AgentEvent event, String recordType) {
        if (!sessionId.equals(event.sessionId())) {
            throw new SessionStoreException(recordType + " does not belong to session " + sessionId);
        }
    }

    private JsonNode readJsonObject(String line, String recordType, int lineNumber) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(line);
        if (root == null || !root.isObject()) {
            throw new SessionStoreException(
                    "Expected " + recordType + " JSON object at line " + lineNumber);
        }
        return root;
    }

    private static String textDiscriminator(JsonNode root, String fieldName, String label, int lineNumber) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new SessionStoreException("Missing text " + label + " at line " + lineNumber);
        }
        return value.asText();
    }

    private AgentMessage toMessage(StoredMessage stored, int lineNumber) {
        try {
            return switch (stored.kind()) {
                case "user" -> objectMapper.treeToValue(stored.json(), UserMessage.class);
                case "assistant" -> objectMapper.treeToValue(stored.json(), AssistantMessage.class);
                case "tool_result" -> objectMapper.treeToValue(stored.json(), ToolResultMessage.class);
                default -> throw new SessionStoreException(
                        "Unknown message kind '" + stored.kind() + "' at line " + lineNumber);
            };
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new SessionStoreException("Failed to convert message at line " + lineNumber, ex);
        }
    }

    private AgentEvent toEvent(StoredEvent stored, int lineNumber) {
        try {
            return switch (stored.type()) {
                case "agent_started" -> objectMapper.treeToValue(stored.json(), AgentStartedEvent.class);
                case "user_message_appended" -> objectMapper.treeToValue(stored.json(), UserMessageAppendedEvent.class);
                case "assistant_delta" -> objectMapper.treeToValue(stored.json(), AssistantDeltaEvent.class);
                case "assistant_completed" -> objectMapper.treeToValue(stored.json(), AssistantCompletedEvent.class);
                case "tool_call_started" -> objectMapper.treeToValue(stored.json(), ToolCallStartedEvent.class);
                case "tool_call_finished" -> objectMapper.treeToValue(stored.json(), ToolCallFinishedEvent.class);
                case "tool_call_denied" -> objectMapper.treeToValue(stored.json(), ToolCallDeniedEvent.class);
                case "agent_error" -> objectMapper.treeToValue(stored.json(), AgentErrorEvent.class);
                case "agent_completed" -> objectMapper.treeToValue(stored.json(), AgentCompletedEvent.class);
                default -> throw new SessionStoreException(
                        "Unknown event type '" + stored.type() + "' at line " + lineNumber);
            };
        } catch (JsonProcessingException | IllegalArgumentException ex) {
            throw new SessionStoreException("Failed to convert event at line " + lineNumber, ex);
        }
    }

    private static void createFileIfMissing(Path path) throws IOException {
        if (Files.notExists(path)) {
            Files.createFile(path);
        }
    }

    private record StoredSession(
            String id,
            String workspaceRoot,
            Instant createdAt,
            Instant updatedAt,
            String title
    ) {

        private static StoredSession from(AgentSession session) {
            return new StoredSession(
                    session.id(),
                    session.workspaceRoot().toString(),
                    session.createdAt(),
                    session.updatedAt(),
                    session.title());
        }

        private AgentSession toAgentSession() {
            return new AgentSession(id, Path.of(workspaceRoot), createdAt, updatedAt, title);
        }
    }
}
