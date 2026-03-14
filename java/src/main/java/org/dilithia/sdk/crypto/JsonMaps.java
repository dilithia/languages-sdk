package org.dilithia.sdk.crypto;

import java.util.LinkedHashMap;
import java.util.Map;

final class JsonMaps {
    private JsonMaps() {}

    static String stringify(Map<String, Object> value) {
        StringBuilder builder = new StringBuilder();
        appendValue(builder, value);
        return builder.toString();
    }

    static Map<String, Object> parse(String payload) {
        String json = payload == null ? "" : payload.trim();
        if (json.isEmpty() || json.charAt(0) != '{' || json.charAt(json.length() - 1) != '}') {
            throw new IllegalStateException("invalid JSON payload");
        }
        return parseObject(json, 1, json.length() - 1);
    }

    private static Map<String, Object> parseObject(String json, int start, int end) {
        Map<String, Object> result = new LinkedHashMap<>();
        int index = start;
        while (index < end) {
            index = skipWhitespace(json, index, end);
            if (index >= end) {
                break;
            }
            ParseResult key = parseString(json, index);
            index = skipWhitespace(json, key.nextIndex, end);
            if (json.charAt(index) != ':') {
                throw new IllegalStateException("expected ':' in JSON object");
            }
            index = skipWhitespace(json, index + 1, end);
            ParseResult value = parseValue(json, index, end);
            result.put((String) key.value, value.value);
            index = skipWhitespace(json, value.nextIndex, end);
            if (index < end && json.charAt(index) == ',') {
                index += 1;
            }
        }
        return result;
    }

    private static ParseResult parseValue(String json, int index, int end) {
        char ch = json.charAt(index);
        if (ch == '"') {
            return parseString(json, index);
        }
        if (ch == '{') {
            int closing = findMatchingBrace(json, index, end);
            return new ParseResult(parseObject(json, index + 1, closing), closing + 1);
        }
        if (json.startsWith("true", index)) {
            return new ParseResult(Boolean.TRUE, index + 4);
        }
        if (json.startsWith("false", index)) {
            return new ParseResult(Boolean.FALSE, index + 5);
        }
        if (json.startsWith("null", index)) {
            return new ParseResult(null, index + 4);
        }
        return parseNumber(json, index, end);
    }

    private static ParseResult parseString(String json, int index) {
        StringBuilder builder = new StringBuilder();
        int cursor = index + 1;
        while (cursor < json.length()) {
            char ch = json.charAt(cursor);
            if (ch == '\\') {
                char escaped = json.charAt(cursor + 1);
                builder.append(escaped);
                cursor += 2;
                continue;
            }
            if (ch == '"') {
                return new ParseResult(builder.toString(), cursor + 1);
            }
            builder.append(ch);
            cursor += 1;
        }
        throw new IllegalStateException("unterminated JSON string");
    }

    private static ParseResult parseNumber(String json, int index, int end) {
        int cursor = index;
        while (cursor < end) {
            char ch = json.charAt(cursor);
            if (!(Character.isDigit(ch) || ch == '-' || ch == '.')) {
                break;
            }
            cursor += 1;
        }
        String raw = json.substring(index, cursor);
        if (raw.contains(".")) {
            return new ParseResult(Double.parseDouble(raw), cursor);
        }
        return new ParseResult(Long.parseLong(raw), cursor);
    }

    private static int findMatchingBrace(String json, int index, int end) {
        int depth = 0;
        for (int cursor = index; cursor < end; cursor++) {
            char ch = json.charAt(cursor);
            if (ch == '{') {
                depth += 1;
            } else if (ch == '}') {
                depth -= 1;
                if (depth == 0) {
                    return cursor;
                }
            }
        }
        throw new IllegalStateException("unbalanced JSON object");
    }

    private static int skipWhitespace(String json, int index, int end) {
        int cursor = index;
        while (cursor < end && Character.isWhitespace(json.charAt(cursor))) {
            cursor += 1;
        }
        return cursor;
    }

    @SuppressWarnings("unchecked")
    private static void appendValue(StringBuilder builder, Object value) {
        if (value == null) {
            builder.append("null");
            return;
        }
        if (value instanceof String text) {
            builder.append('"');
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '"' || ch == '\\') {
                    builder.append('\\');
                }
                builder.append(ch);
            }
            builder.append('"');
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            builder.append(value);
            return;
        }
        if (value instanceof Map<?, ?> mapValue) {
            builder.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (!first) {
                    builder.append(',');
                }
                first = false;
                appendValue(builder, String.valueOf(entry.getKey()));
                builder.append(':');
                appendValue(builder, entry.getValue());
            }
            builder.append('}');
            return;
        }
        throw new IllegalStateException("unsupported JSON value: " + value.getClass().getName());
    }

    private record ParseResult(Object value, int nextIndex) {}
}
