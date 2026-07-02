package com.example.pispringai.model;

import com.example.pispringai.agent.AgentPromptCommand;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class SpringAiModelClient implements ModelClient {

    private final ChatClient chatClient;
    private final SpringAiToolCallbackFactory toolCallbackFactory;

    public SpringAiModelClient(ChatClient chatClient, SpringAiToolCallbackFactory toolCallbackFactory) {
        this.chatClient = Objects.requireNonNull(chatClient, "chatClient must not be null");
        this.toolCallbackFactory = Objects.requireNonNull(
                toolCallbackFactory,
                "toolCallbackFactory must not be null");
    }

    @Override
    public Flux<String> streamText(AgentPromptCommand command, SpringAiToolCallbackEvents events) {
        Objects.requireNonNull(command, "command must not be null");
        return chatClient.prompt()
                .user(command.userText())
                .tools(toolCallbackFactory.callbacksFor(command.options(), command.workspaceRoot(), events))
                .stream()
                .content();
    }
}
