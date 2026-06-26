package com.example.pispringai.agent;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentRunOptionsTest {

    @Test
    void safeDefaultsAreReadOnlyAndBounded() {
        AgentRunOptions options = AgentRunOptions.safeDefaults();

        assertThat(options.allowWrite()).isFalse();
        assertThat(options.allowBash()).isFalse();
        assertThat(options.maxTurns()).isEqualTo(6);
        assertThat(options.maxToolCalls()).isEqualTo(12);
        assertThat(options.timeout()).isEqualTo(Duration.ofSeconds(120));
    }

    @Test
    void rejectsInvalidExecutionBounds() {
        assertThatThrownBy(() -> new AgentRunOptions(false, false, 0, 12, Duration.ofSeconds(120)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxTurns must be positive");
        assertThatThrownBy(() -> new AgentRunOptions(false, false, 6, 0, Duration.ofSeconds(120)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxToolCalls must be positive");
        assertThatThrownBy(() -> new AgentRunOptions(false, false, 6, 12, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("timeout must be positive");
        assertThatThrownBy(() -> new AgentRunOptions(false, false, 6, 12, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("timeout must not be null");
    }

    @Test
    void promptCommandRequiresUsableInput() {
        AgentPromptCommand command = new AgentPromptCommand(
                "s_1",
                "Analyze this project",
                Path.of("."),
                AgentRunOptions.safeDefaults());

        assertThat(command.sessionId()).isEqualTo("s_1");
        assertThat(command.userText()).isEqualTo("Analyze this project");
        assertThat(command.workspaceRoot()).isEqualTo(Path.of("."));
        assertThat(command.options()).isEqualTo(AgentRunOptions.safeDefaults());

        assertThatThrownBy(() -> new AgentPromptCommand(
                " ",
                "Analyze this project",
                Path.of("."),
                AgentRunOptions.safeDefaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sessionId must not be blank");

        assertThatThrownBy(() -> new AgentPromptCommand(
                "s_1",
                " ",
                Path.of("."),
                AgentRunOptions.safeDefaults()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userText must not be blank");

        assertThatThrownBy(() -> new AgentPromptCommand(
                "s_1",
                "Analyze this project",
                null,
                AgentRunOptions.safeDefaults()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("workspaceRoot must not be null");

        assertThatThrownBy(() -> new AgentPromptCommand(
                "s_1",
                "Analyze this project",
                Path.of("."),
                null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("options must not be null");
    }
}
