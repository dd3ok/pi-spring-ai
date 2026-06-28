package com.example.pispringai.agent;

import java.util.UUID;

public final class IdGenerator {

    private IdGenerator() {
    }

    public static String sessionId() {
        return prefixed("s");
    }

    public static String messageId() {
        return prefixed("m");
    }

    public static String eventId() {
        return prefixed("e");
    }

    public static String toolCallId() {
        return prefixed("tc");
    }

    private static String prefixed(String prefix) {
        return prefix + "_" + UUID.randomUUID();
    }
}
