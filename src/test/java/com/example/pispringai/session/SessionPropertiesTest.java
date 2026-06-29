package com.example.pispringai.session;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionPropertiesTest {

    @Test
    void usesDefaultSessionRootWhenNotConfigured() {
        SessionProperties properties = new SessionProperties(null);

        assertThat(properties.root())
                .isEqualTo(Path.of(".pi-spring-ai/sessions").toAbsolutePath().normalize());
    }
}
