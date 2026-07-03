package com.example.pispringai.agent;

import com.example.pispringai.agent.event.AgentEvent;
import reactor.core.publisher.Flux;

public interface AgentRuntime {

    Flux<AgentEvent> prompt(AgentPromptCommand command);
}
