package com.example.pispringai.model;

import com.example.pispringai.agent.AgentPromptCommand;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.agent.message.AssistantMessage;
import com.example.pispringai.agent.message.ToolResultMessage;
import com.example.pispringai.agent.message.UserMessage;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.Message;
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
        ChatClientRequestSpec request = chatClient.prompt();
        List<Message> messages = toSpringAiMessages(command.messages());
        request = messages.isEmpty()
                ? request.user(command.userText())
                : request.messages(messages);
        return request
                .tools(toolCallbackFactory.callbacksFor(command.options(), command.workspaceRoot(), events))
                .stream()
                .content();
    }

    private static List<Message> toSpringAiMessages(List<AgentMessage> messages) {
        return messages.stream()
                .map(SpringAiModelClient::toSpringAiMessage)
                .toList();
    }

    private static Message toSpringAiMessage(AgentMessage message) {
        return switch (message) {
            case UserMessage user -> new org.springframework.ai.chat.messages.UserMessage(user.text());
            case AssistantMessage assistant -> new org.springframework.ai.chat.messages.AssistantMessage(assistant.text());
            case ToolResultMessage toolResult -> new org.springframework.ai.chat.messages.UserMessage(toolResultText(toolResult));
        };
    }

    private static String toolResultText(ToolResultMessage toolResult) {
        String status = toolResult.error() ? "failed" : "returned";
        return "Tool " + toolResult.toolName() + " " + status + ":\n" + toolResult.resultText();
    }
}
