package com.jruk8.jwarp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class MessageFormatter {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageFormatter() {
    }

    public static Component format(String template, String prefix) {
        String value = template == null ? "" : template;
        String resolvedPrefix = prefix == null ? "" : prefix;
        return MINI_MESSAGE.deserialize(value.replace("{prefix}", resolvedPrefix));
    }
}
