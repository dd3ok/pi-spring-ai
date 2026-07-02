package com.example.pispringai.api;

import com.example.pispringai.agent.AgentPromptCommand;
import com.example.pispringai.agent.AgentRuntime;
import com.example.pispringai.agent.event.AgentEvent;
import com.example.pispringai.session.AgentSession;
import com.example.pispringai.session.SessionStore;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/sessions")
public class AgentController {

    private final SessionStore sessionStore;
    private final AgentRuntime agentRuntime;

    public AgentController(SessionStore sessionStore, AgentRuntime agentRuntime) {
        this.sessionStore = sessionStore;
        this.agentRuntime = agentRuntime;
    }

    @PostMapping
    public Mono<CreateSessionResponse> createSession(@RequestBody CreateSessionRequest request) {
        return Mono.fromCallable(() -> sessionStore.create(workspaceRoot(request.workspaceRoot())))
                .subscribeOn(Schedulers.boundedElastic())
                .map(session -> new CreateSessionResponse(session.id(), session.workspaceRoot().toString()));
    }

    @PostMapping(
            value = "/{sessionId}/messages/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentEvent>> streamMessage(
            @PathVariable String sessionId,
            @RequestBody AgentPromptRequest request
    ) {
        return Mono.fromCallable(() -> findSession(sessionId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(session -> agentRuntime.prompt(new AgentPromptCommand(
                        session.id(),
                        request.text(),
                        session.workspaceRoot(),
                        request.toRunOptions())))
                .map(event -> ServerSentEvent.<AgentEvent>builder(event)
                        .id(event.eventId())
                        .event("agent_event")
                        .build());
    }

    private AgentSession findSession(String sessionId) {
        return sessionStore.findSession(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private static Path workspaceRoot(String rawWorkspaceRoot) {
        try {
            return Path.of(rawWorkspaceRoot);
        } catch (InvalidPathException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid workspace root path", exception);
        }
    }
}
