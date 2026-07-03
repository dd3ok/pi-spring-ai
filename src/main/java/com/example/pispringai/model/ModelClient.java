package com.example.pispringai.model;

import com.example.pispringai.agent.AgentPromptCommand;
import reactor.core.publisher.Flux;

public interface ModelClient {

    Flux<String> streamText(AgentPromptCommand command, SpringAiToolCallbackEvents events);
}
