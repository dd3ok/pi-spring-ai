package com.example.pispringai.tool.builtin;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

final class TextFileReader {

    private TextFileReader() {
    }

    static String decodeUtf8Text(byte[] bytes) throws CharacterCodingException {
        if (containsNul(bytes)) {
            throw new CharacterCodingException();
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }

    private static boolean containsNul(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }
}
