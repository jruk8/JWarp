package com.jruk8.jwarp;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {
    @Test
    void formatsPrefixAndMiniMessageTags() {
        var component = MessageFormatter.format("{prefix}<green>Hello", "<dark_gray>[<aqua>JWarp<dark_gray>] ");

        assertEquals(
            "[JWarp] Hello",
            PlainTextComponentSerializer.plainText().serialize(component)
        );
    }
}
