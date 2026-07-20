package com.jruk8.jwarp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageFormatterTest {
    @Test
    void formatsPrefixAndColorCodes() {
        String result = MessageFormatter.format("{prefix}&aHello", "&8[&bJWarp&8] ");

        assertEquals("\u00A78[\u00A7bJWarp\u00A78] \u00A7aHello", result);
    }

    @Test
    void colorizeHandlesPlainText() {
        assertEquals("Hello world", MessageFormatter.colorize("Hello world"));
    }
}
