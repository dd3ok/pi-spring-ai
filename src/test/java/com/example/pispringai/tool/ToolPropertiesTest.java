package com.example.pispringai.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPropertiesTest {

    @Test
    void usesDefaultMaxToolOutputCharsWhenNotConfigured() {
        ToolProperties properties = new ToolProperties(0);

        assertThat(properties.maxToolOutputChars()).isEqualTo(20_000);
    }

    @Test
    void keepsConfiguredPositiveMaxToolOutputChars() {
        ToolProperties properties = new ToolProperties(25);

        assertThat(properties.maxToolOutputChars()).isEqualTo(25);
    }
}
