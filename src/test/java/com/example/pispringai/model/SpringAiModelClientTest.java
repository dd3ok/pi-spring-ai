package com.example.pispringai.model;

import com.example.pispringai.agent.AgentPromptCommand;
import com.example.pispringai.agent.AgentRunOptions;
import com.example.pispringai.agent.message.AgentMessage;
import com.example.pispringai.agent.message.AssistantMessage;
import com.example.pispringai.agent.message.AssistantStopReason;
import com.example.pispringai.agent.message.UserMessage;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringAiModelClientTest {

    @Test
    void streamTextSendsCommandMessagesToChatClient() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        SpringAiToolCallbackFactory toolCallbackFactory = mock(SpringAiToolCallbackFactory.class);
        AgentPromptCommand command = commandWithMessages();
        when(chatClient.prompt()).thenReturn(request);
        when(request.messages(anyList())).thenReturn(request);
        when(request.tools(any(Object[].class))).thenReturn(request);
        when(request.stream()).thenReturn(stream);
        when(stream.content()).thenReturn(Flux.just("model"));
        when(toolCallbackFactory.callbacksFor(any(), any(), any())).thenReturn(List.of());
        SpringAiModelClient client = new SpringAiModelClient(chatClient, toolCallbackFactory);

        List<String> chunks = client.streamText(command, SpringAiToolCallbackEvents.noop()).collectList().block();

        assertThat(chunks).containsExactly("model");
        verify(request, never()).user(anyString());
        ArgumentCaptor<List<Message>> messages = messageListCaptor();
        verify(request).messages(messages.capture());
        assertThat(messages.getValue()).hasSize(3);
        assertThat(messages.getValue().get(0)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
        assertThat(messages.getValue().get(0).getText()).isEqualTo("first");
        assertThat(messages.getValue().get(1)).isInstanceOf(org.springframework.ai.chat.messages.AssistantMessage.class);
        assertThat(messages.getValue().get(1).getText()).isEqualTo("answer");
        assertThat(messages.getValue().get(2)).isInstanceOf(org.springframework.ai.chat.messages.UserMessage.class);
        assertThat(messages.getValue().get(2).getText()).isEqualTo("follow up");
    }

    private static AgentPromptCommand commandWithMessages() {
        return new AgentPromptCommand(
                "s_1",
                "follow up",
                Path.of("."),
                AgentRunOptions.safeDefaults(),
                List.<AgentMessage>of(
                        new UserMessage("m_1", "s_1", null, Instant.parse("2026-07-03T00:00:00Z"), "first"),
                        new AssistantMessage(
                                "m_2",
                                "s_1",
                                "m_1",
                                Instant.parse("2026-07-03T00:00:01Z"),
                                "answer",
                                AssistantStopReason.STOP),
                        new UserMessage(
                                "m_3",
                                "s_1",
                                "m_2",
                                Instant.parse("2026-07-03T00:00:02Z"),
                                "follow up")));
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Message>> messageListCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
