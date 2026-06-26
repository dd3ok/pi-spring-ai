package com.example.pispringai.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

@SpringBootTest(properties = {
        "AGENT_MODEL_PROVIDER=google-genai",
        "GEMINI_MODEL=gemini-2.5-flash",
        "AGENT_MODEL_NAME=gemini-2.5-flash",
        "OPENAI_MODEL=gpt-4.1-mini",
        "spring.ai.google.genai.api-key=test-gemini-api-key"
})
class ModelProviderConfigurationTest {

    @Autowired
    private Environment environment;

    @Test
    void contextResolvesGeminiGoogleGenaiProvider() {
        assertThat(environment.getProperty("spring.ai.model.chat")).isEqualTo("google-genai");
        assertThat(environment.getProperty("spring.ai.google.genai.chat.model")).isEqualTo("gemini-2.5-flash");
        assertThat(environment.getProperty("agent.model.provider")).isEqualTo("google-genai");
        assertThat(environment.getProperty("agent.model.name")).isEqualTo("gemini-2.5-flash");
    }

    @Test
    void keepsOpenAiProviderConfigurationAvailable() {
        assertThat(environment.getProperty("spring.ai.openai.chat.model")).isEqualTo("gpt-4.1-mini");
    }

    @Test
    void applicationYamlDefinesGeminiDefaultsAndOpenAiFallbacks() throws IOException {
        PropertySource<?> applicationYaml = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(applicationYaml.getProperty("spring.ai.model.chat"))
                .isEqualTo("${AGENT_MODEL_PROVIDER:google-genai}");
        assertThat(applicationYaml.getProperty("spring.ai.google.genai.chat.model"))
                .isEqualTo("${GEMINI_MODEL:gemini-2.5-flash}");
        assertThat(applicationYaml.getProperty("agent.model.provider"))
                .isEqualTo("${AGENT_MODEL_PROVIDER:google-genai}");
        assertThat(applicationYaml.getProperty("agent.model.name"))
                .isEqualTo("${AGENT_MODEL_NAME:${GEMINI_MODEL:gemini-2.5-flash}}");
        assertThat(applicationYaml.getProperty("spring.ai.openai.chat.model"))
                .isEqualTo("${OPENAI_MODEL:gpt-4.1-mini}");
    }
}
