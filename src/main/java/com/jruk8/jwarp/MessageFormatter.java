package com.jruk8.jwarp;

public final class MessageFormatter {
    private MessageFormatter() {
    }

    public static String format(String template, String prefix) {
        String value = template == null ? "" : template;
        String resolvedPrefix = prefix == null ? "" : prefix;
        return colorize(value.replace("{prefix}", resolvedPrefix));
    }

    public static String colorize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder(input.length());

        for (int index = 0; index < input.length(); index++) {
            char character = input.charAt(index);

            if (character == '&' && index + 1 < input.length()) {
                output.append('§');
                output.append(input.charAt(++index));
                continue;
            }

            output.append(character);
        }

        return output.toString();
    }
}