package com.example.pispringai.api;

import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.session.AgentSession;
import java.time.Instant;
import java.util.List;

public record SessionHistoryResponse(
        SessionSummary session,
        List<AgentMessage> messages,
        List<AgentEvent> events
) {

    public static SessionHistoryResponse from(
            AgentSession session,
            List<AgentMessage> messages,
            List<AgentEvent> events) {
        return new SessionHistoryResponse(
                SessionSummary.from(session),
                List.copyOf(messages),
                List.copyOf(events));
    }

    public record SessionSummary(
            String id,
            String workspaceRoot,
            Instant createdAt,
            Instant updatedAt,
            String title
    ) {

        private static SessionSummary from(AgentSession session) {
            return new SessionSummary(
                    session.id(),
                    session.workspaceRoot().toString(),
                    session.createdAt(),
                    session.updatedAt(),
                    session.title());
        }
    }
}
