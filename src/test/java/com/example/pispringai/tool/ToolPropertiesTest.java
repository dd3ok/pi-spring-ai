package com.example.pispringai.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ToolPropertiesTest {

    @Test
    void usesDefaultMaxToolOutputCharsWhenNotConfigured() {
        ToolProperties properties = new ToolProperties(0, 30);

        assertThat(properties.maxToolOutputChars()).isEqualTo(20_000);
    }

    @Test
    void keepsConfiguredPositiveMaxToolOutputChars() {
        ToolProperties properties = new ToolProperties(25, 30);

        assertThat(properties.maxToolOutputChars()).isEqualTo(25);
    }

    @Test
    void usesDefaultBashTimeoutSecondsWhenNotConfigured() {
        ToolProperties properties = new ToolProperties(25, 0);

        assertThat(properties.bashTimeoutSeconds()).isEqualTo(30);
    }

    @Test
    void keepsConfiguredPositiveBashTimeoutSeconds() {
        ToolProperties properties = new ToolProperties(25, 2);

        assertThat(properties.bashTimeoutSeconds()).isEqualTo(2);
    }
}
