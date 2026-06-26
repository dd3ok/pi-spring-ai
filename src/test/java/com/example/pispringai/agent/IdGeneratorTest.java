package com.example.pispringai.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void generatedIdsUseReadablePrefixesAndAreUnique() {
        String firstSessionId = IdGenerator.sessionId();
        String secondSessionId = IdGenerator.sessionId();

        assertThat(firstSessionId).startsWith("s_");
        assertThat(secondSessionId).startsWith("s_");
        assertThat(firstSessionId).isNotEqualTo(secondSessionId);

        assertThat(IdGenerator.messageId()).startsWith("m_");
        assertThat(IdGenerator.eventId()).startsWith("e_");
        assertThat(IdGenerator.toolCallId()).startsWith("tc_");
    }
}
