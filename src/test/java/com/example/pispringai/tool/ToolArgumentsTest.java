package com.example.pispringai.tool;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolArgumentsTest {

    @Test
    void returnsRequiredAndOptionalStringArguments() {
        ToolArguments arguments = new ToolArguments(Map.of("path", "README.md"));

        assertThat(arguments.requiredString("path")).isEqualTo("README.md");
        assertThat(arguments.optionalString("path")).contains("README.md");
        assertThat(arguments.optionalString("missing")).isEmpty();
    }

    @Test
    void rejectsMissingBlankOrNonStringRequiredArgument() {
        assertThatThrownBy(() -> new ToolArguments(Map.of()).requiredString("path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");

        assertThatThrownBy(() -> new ToolArguments(Map.of("path", " ")).requiredString("path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");

        assertThatThrownBy(() -> new ToolArguments(Map.of("path", 123)).requiredString("path"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }
}
