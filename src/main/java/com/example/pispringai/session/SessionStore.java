package com.example.pispringai.session;

import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.agent.message.AgentMessage;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface SessionStore {

    AgentSession create(Path workspaceRoot);

    Optional<AgentSession> findSession(String sessionId);

    void appendMessage(String sessionId, AgentMessage message);

    void appendEvent(String sessionId, AgentEvent event);

    List<AgentMessage> loadMessages(String sessionId);

    List<AgentEvent> loadEvents(String sessionId);
}
