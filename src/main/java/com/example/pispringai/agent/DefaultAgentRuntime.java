package com.example.pispringai.agent;

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
import com.example.pispringai.agent.message.AssistantMessage;
import com.example.pispringai.agent.message.AssistantStopReason;
import com.example.pispringai.agent.message.ToolResultMessage;
import com.example.pispringai.agent.message.UserMessage;
import com.example.pispringai.model.ModelClient;
import com.example.pispringai.model.SpringAiToolCallbackEvents;
import com.example.pispringai.session.SessionStore;
import com.example.pispringai.tool.ToolArguments;
import com.example.pispringai.tool.ToolResult;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

@Service
public class DefaultAgentRuntime implements AgentRuntime {

    private final SessionStore sessionStore;
    private final ModelClient modelClient;

    public DefaultAgentRuntime(SessionStore sessionStore, ModelClient modelClient) {
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore must not be null");
        this.modelClient = Objects.requireNonNull(modelClient, "modelClient must not be null");
    }

    @Override
    public Flux<AgentEvent> prompt(AgentPromptCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return Flux.<AgentEvent>create(emitter -> startRun(command, emitter))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void startRun(AgentPromptCommand command, FluxSink<AgentEvent> emitter) {
        RunState state = new RunState();
        StringBuilder assistantText = new StringBuilder();
        AtomicBoolean finished = new AtomicBoolean(false);
        SpringAiToolCallbackEvents toolEvents = new RuntimeToolCallbackEvents(command.sessionId(), state, emitter);

        try {
            emit(command.sessionId(), state, emitter, new AgentStartedEvent(
                    IdGenerator.eventId(),
                    command.sessionId(),
                    Instant.now()));

            UserMessage userMessage = new UserMessage(
                    IdGenerator.messageId(),
                    command.sessionId(),
                    null,
                    Instant.now(),
                    command.userText());
            synchronized (state) {
                sessionStore.appendMessage(command.sessionId(), userMessage);
                state.updateLastMessageId(userMessage.id());
                appendEvent(command.sessionId(), emitter, new UserMessageAppendedEvent(
                        IdGenerator.eventId(),
                        command.sessionId(),
                        Instant.now(),
                        userMessage.id()));
            }

            Flux<String> modelStream = Objects.requireNonNull(
                    modelClient.streamText(command, toolEvents),
                    "model stream must not be null");
            Disposable disposable = modelStream.subscribe(
                    text -> appendAssistantDelta(command.sessionId(), state, assistantText, emitter, text),
                    error -> completeWithError(command.sessionId(), state, emitter, finished, error),
                    () -> completeSuccessfully(command.sessionId(), emitter, state, assistantText, finished));
            emitter.onDispose(disposable);
        } catch (RuntimeException exception) {
            completeWithError(command.sessionId(), state, emitter, finished, exception);
        }
    }

    private void appendAssistantDelta(
            String sessionId,
            RunState state,
            StringBuilder assistantText,
            FluxSink<AgentEvent> emitter,
            String text
    ) {
        if (text == null) {
            return;
        }
        synchronized (state) {
            assistantText.append(text);
            appendEvent(sessionId, emitter, new AssistantDeltaEvent(IdGenerator.eventId(), sessionId, Instant.now(), text));
        }
    }

    private void completeSuccessfully(
            String sessionId,
            FluxSink<AgentEvent> emitter,
            RunState state,
            StringBuilder assistantText,
            AtomicBoolean finished
    ) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        synchronized (state) {
            AssistantMessage assistantMessage = new AssistantMessage(
                    IdGenerator.messageId(),
                    sessionId,
                    state.lastMessageId(),
                    Instant.now(),
                    assistantText.toString(),
                    AssistantStopReason.STOP);
            sessionStore.appendMessage(sessionId, assistantMessage);
            state.updateLastMessageId(assistantMessage.id());
            appendEvent(sessionId, emitter, new AssistantCompletedEvent(
                    IdGenerator.eventId(),
                    sessionId,
                    Instant.now(),
                    assistantMessage.id()));
            appendEvent(sessionId, emitter, new AgentCompletedEvent(IdGenerator.eventId(), sessionId, Instant.now()));
            emitter.complete();
        }
    }

    private void completeWithError(
            String sessionId,
            RunState state,
            FluxSink<AgentEvent> emitter,
            AtomicBoolean finished,
            Throwable error
    ) {
        if (!finished.compareAndSet(false, true)) {
            return;
        }
        try {
            synchronized (state) {
                appendEvent(sessionId, emitter, new AgentErrorEvent(
                        IdGenerator.eventId(),
                        sessionId,
                        Instant.now(),
                        errorMessage(error),
                        "MODEL_ERROR",
                        false));
                appendEvent(sessionId, emitter, new AgentCompletedEvent(IdGenerator.eventId(), sessionId, Instant.now()));
                emitter.complete();
            }
        } catch (RuntimeException appendFailure) {
            emitter.error(appendFailure);
        }
    }

    private void appendToolResult(String sessionId, RunState state, String callId, String toolName, ToolResult result) {
        ToolResultMessage message = new ToolResultMessage(
                IdGenerator.messageId(),
                sessionId,
                state.lastMessageId(),
                Instant.now(),
                callId,
                toolName,
                result.text(),
                result.error());
        sessionStore.appendMessage(sessionId, message);
        state.updateLastMessageId(message.id());
    }

    private void emit(String sessionId, RunState state, FluxSink<AgentEvent> emitter, AgentEvent event) {
        synchronized (state) {
            appendEvent(sessionId, emitter, event);
        }
    }

    private void appendEvent(String sessionId, FluxSink<AgentEvent> emitter, AgentEvent event) {
        sessionStore.appendEvent(sessionId, event);
        emitter.next(event);
    }

    private static ToolResult normalizeResult(ToolResult result) {
        return result == null
                ? ToolResult.error("tool execution returned no result")
                : result;
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        return message == null || message.isBlank()
                ? error.getClass().getSimpleName()
                : message;
    }

    private final class RuntimeToolCallbackEvents implements SpringAiToolCallbackEvents {

        private final String sessionId;
        private final RunState state;
        private final FluxSink<AgentEvent> emitter;

        private RuntimeToolCallbackEvents(String sessionId, RunState state, FluxSink<AgentEvent> emitter) {
            this.sessionId = sessionId;
            this.state = state;
            this.emitter = emitter;
        }

        @Override
        public void toolCallStarted(String toolName, ToolArguments arguments) {
            String callId = state.startToolCall(toolName);
            toolCallStarted(callId, toolName, arguments);
        }

        @Override
        public void toolCallStarted(String toolCallId, String toolName, ToolArguments arguments) {
            emit(sessionId, state, emitter, new ToolCallStartedEvent(
                    IdGenerator.eventId(),
                    sessionId,
                    Instant.now(),
                    toolCallId,
                    toolName));
        }

        @Override
        public void toolCallFinished(String toolName, ToolResult result) {
            String callId = state.finishToolCall(toolName);
            toolCallFinished(callId, toolName, result);
        }

        @Override
        public void toolCallFinished(String toolCallId, String toolName, ToolResult result) {
            ToolResult normalized = normalizeResult(result);
            synchronized (state) {
                appendToolResult(sessionId, state, toolCallId, toolName, normalized);
                appendEvent(sessionId, emitter, new ToolCallFinishedEvent(
                        IdGenerator.eventId(),
                        sessionId,
                        Instant.now(),
                        toolCallId,
                        toolName,
                        normalized.error()));
            }
        }

        @Override
        public void toolCallDenied(String toolName, ToolResult result) {
            String callId = state.finishToolCall(toolName);
            toolCallDenied(callId, toolName, result);
        }

        @Override
        public void toolCallDenied(String toolCallId, String toolName, ToolResult result) {
            ToolResult normalized = normalizeResult(result);
            synchronized (state) {
                appendToolResult(sessionId, state, toolCallId, toolName, normalized);
                appendEvent(sessionId, emitter, new ToolCallDeniedEvent(
                        IdGenerator.eventId(),
                        sessionId,
                        Instant.now(),
                        toolCallId,
                        toolName,
                        normalized.text()));
            }
        }

        @Override
        public void toolCallFailed(String toolName, ToolResult result) {
            toolCallFailed(IdGenerator.toolCallId(), toolName, result);
        }

        @Override
        public void toolCallFailed(String toolCallId, String toolName, ToolResult result) {
            ToolResult normalized = normalizeResult(result);
            synchronized (state) {
                appendToolResult(sessionId, state, toolCallId, toolName, normalized);
                appendEvent(sessionId, emitter, new AgentErrorEvent(
                        IdGenerator.eventId(),
                        sessionId,
                        Instant.now(),
                        toolName + ": " + normalized.text(),
                        "TOOL_CALL_FAILED",
                        true));
            }
        }
    }

    private static final class RunState {

        private final Deque<PendingToolCall> pendingToolCalls = new ArrayDeque<>();
        private String lastMessageId;

        private synchronized String startToolCall(String toolName) {
            String callId = IdGenerator.toolCallId();
            pendingToolCalls.addLast(new PendingToolCall(callId, toolName));
            return callId;
        }

        private synchronized String finishToolCall(String toolName) {
            Iterator<PendingToolCall> iterator = pendingToolCalls.iterator();
            while (iterator.hasNext()) {
                PendingToolCall pending = iterator.next();
                if (pending.toolName().equals(toolName)) {
                    iterator.remove();
                    return pending.callId();
                }
            }
            return IdGenerator.toolCallId();
        }

        private synchronized String lastMessageId() {
            return lastMessageId;
        }

        private synchronized void updateLastMessageId(String messageId) {
            this.lastMessageId = messageId;
        }
    }

    private record PendingToolCall(String callId, String toolName) {
    }
}
