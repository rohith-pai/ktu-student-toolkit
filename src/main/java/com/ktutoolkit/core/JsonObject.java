package com.ktutoolkit.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal parser for flat JSON objects with string values, used when reading
 * profile data back from Supabase jsonb columns.
 */
public final class JsonObject {
    private JsonObject() {
    }

    public static Map<String, String> parse(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        String text = json == null ? "" : json.trim();
        if (!text.startsWith("{") || !text.endsWith("}")) {
            return values;
        }
        text = text.substring(1, text.length() - 1);

        int index = 0;
        while (index < text.length()) {
            while (index < text.length() && (text.charAt(index) == ',' || isSpace(text.charAt(index)))) {
                index++;
            }
            Parsed key = readString(text, index);
            if (key == null) {
                break;
            }
            index = key.end;
            while (index < text.length() && isSpace(text.charAt(index))) {
                index++;
            }
            if (index >= text.length() || text.charAt(index) != ':') {
                break;
            }
            index++;
            while (index < text.length() && isSpace(text.charAt(index))) {
                index++;
            }
            Parsed value = readString(text, index);
            if (value == null) {
                break;
            }
            values.put(key.value, value.value);
            index = value.end;
        }
        return values;
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c);
    }

    private static Parsed readString(String text, int start) {
        if (start >= text.length() || text.charAt(start) != '"') {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        int index = start + 1;
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') {
                return new Parsed(builder.toString(), index);
            }
            if (c != '\\') {
                builder.append(c);
                continue;
            }
            if (index >= text.length()) {
                return null;
            }
            char escaped = text.charAt(index++);
            switch (escaped) {
                case '"': builder.append('"'); break;
                case '\\': builder.append('\\'); break;
                case '/': builder.append('/'); break;
                case 'n': builder.append('\n'); break;
                case 'r': builder.append('\r'); break;
                case 't': builder.append('\t'); break;
                case 'b': builder.append('\b'); break;
                case 'f': builder.append('\f'); break;
                case 'u':
                    if (index + 4 > text.length()) {
                        return null;
                    }
                    try {
                        builder.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                    index += 4;
                    break;
                default: builder.append(escaped);
            }
        }
        return null;
    }

    private static final class Parsed {
        private final String value;
        private final int end;

        private Parsed(String value, int end) {
            this.value = value;
            this.end = end;
        }
    }
}
