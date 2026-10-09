package se.deversity.common.license.internal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import se.deversity.vibetags.annotations.AIContext;

/**
 * Minimal, dependency-free JSON parser and string escaper.
 *
 * <p>Intentionally small: supports the full JSON grammar for parsing (objects, arrays,
 * strings, numbers, booleans, null) returning nested {@code Map<String,Object>} /
 * {@code List<Object>} / {@code String} / {@code Number} / {@code Boolean} / {@code null}.
 *
 * <p>Not a general-purpose library — we only read 3-4 fields from Keygen's
 * {@code validate-key} response. Keeping this in-tree lets the library ship with
 * zero runtime dependencies.
 *
 * <p><strong>This is an internal API.</strong> Not for consumer use.
 */
@AIContext(
    focus = "Internal, not public API. It exists only so the library can ship zero runtime dependencies "
        + "while reading a handful of fields from Keygen's validate-key response. Keep it small; do not "
        + "widen it into a general-purpose library or expose it from the license package.",
    avoids = "Jackson, Gson, org.json, javax.json — adding any of them defeats the reason this class exists"
)
public final class Json {

    private Json() {
    }

    /** Parse a JSON document. Returns a {@code Map}, {@code List}, boxed primitive, or {@code null}. */
    public static Object parse(String src) {
        Parser p = new Parser(src);
        p.skipWs();
        Object value = p.readValue();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("Trailing content at offset " + p.pos);
        }
        return value;
    }

    /** Convenience: fetch a nested path from a parsed object, returning {@code null} if any segment is missing. */
    @SuppressWarnings("unchecked")
    public static Object get(Object root, String... path) {
        Object cur = root;
        for (String seg : path) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = ((Map<String, Object>) m).get(seg);
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    /** Escape a string for inclusion in a JSON document (without surrounding quotes). */
    public static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\b' -> b.append("\\b");
                case '\f' -> b.append("\\f");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.toString();
    }

    /**
     * Nesting bound. The parser recurses once per object/array level, so an unbounded depth turns
     * a hostile or corrupt body into a {@link StackOverflowError} that escapes every caller's
     * {@code IllegalArgumentException} handler, and with it the fail-closed mapping. Real
     * Keygen and LemonSqueezy responses nest fewer than ten levels.
     */
    static final int MAX_DEPTH = 256;

    private static final class Parser {
        final String src;
        int pos;
        int depth;

        Parser(String src) {
            this.src = src;
        }

        Object readValue() {
            skipWs();
            if (pos >= src.length()) {
                throw err("Unexpected end of input");
            }
            char c = src.charAt(pos);
            if ((c == '{' || c == '[') && depth >= MAX_DEPTH) {
                throw err("Nesting deeper than " + MAX_DEPTH);
            }
            return switch (c) {
                case '{' -> readNested(this::readObject);
                case '[' -> readNested(this::readArray);
                case '"' -> readString();
                case 't', 'f' -> readBool();
                case 'n' -> readNull();
                default -> readNumber();
            };
        }

        Object readNested(java.util.function.Supplier<Object> reader) {
            depth++;
            try {
                return reader.get();
            } finally {
                depth--;
            }
        }

        Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> out = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') {
                pos++;
                return out;
            }
            while (true) {
                skipWs();
                String key = readString();
                skipWs();
                expect(':');
                Object value = readValue();
                out.put(key, value);
                skipWs();
                char n = next();
                if (n == ',') {
                    continue;
                } else if (n == '}') {
                    return out;
                } else {
                    throw err("Expected ',' or '}'");
                }
            }
        }

        List<Object> readArray() {
            expect('[');
            List<Object> out = new ArrayList<>();
            skipWs();
            if (peek() == ']') {
                pos++;
                return out;
            }
            while (true) {
                out.add(readValue());
                skipWs();
                char n = next();
                if (n == ',') {
                    continue;
                } else if (n == ']') {
                    return out;
                } else {
                    throw err("Expected ',' or ']'");
                }
            }
        }

        String readString() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (pos < src.length()) {
                char c = src.charAt(pos++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    if (pos >= src.length()) {
                        throw err("Unterminated escape");
                    }
                    char esc = src.charAt(pos++);
                    switch (esc) {
                        case '"'  -> b.append('"');
                        case '\\' -> b.append('\\');
                        case '/'  -> b.append('/');
                        case 'b'  -> b.append('\b');
                        case 'f'  -> b.append('\f');
                        case 'n'  -> b.append('\n');
                        case 'r'  -> b.append('\r');
                        case 't'  -> b.append('\t');
                        case 'u'  -> {
                            if (pos + 4 > src.length()) {
                                throw err("Truncated \\u escape");
                            }
                            int cp = 0;
                            for (int i = 0; i < 4; i++) {
                                // Not Integer.parseInt: it accepts a leading '+' or '-'.
                                int d = hexDigit(src.charAt(pos + i));
                                if (d < 0) {
                                    throw err("Bad \\u escape");
                                }
                                cp = (cp << 4) | d;
                            }
                            pos += 4;
                            b.append((char) cp);
                        }
                        default -> throw err("Bad escape \\" + esc);
                    }
                } else {
                    b.append(c);
                }
            }
            throw err("Unterminated string");
        }

        Boolean readBool() {
            if (src.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw err("Expected boolean");
        }

        Object readNull() {
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw err("Expected null");
        }

        Number readNumber() {
            int start = pos;
            if (peek() == '-') {
                pos++;
            }
            while (pos < src.length() && isNumChar(src.charAt(pos))) {
                pos++;
            }
            String num = src.substring(start, pos);
            if (num.isEmpty() || "-".equals(num)) {
                throw err("Expected number");
            }
            if (num.contains(".") || num.contains("e") || num.contains("E")) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                return Double.parseDouble(num);
            }
        }

        /** ASCII hex only; {@link Character#digit(char, int)} also accepts fullwidth and other digits. */
        static int hexDigit(char c) {
            if (c >= '0' && c <= '9') return c - '0';
            if (c >= 'a' && c <= 'f') return c - 'a' + 10;
            if (c >= 'A' && c <= 'F') return c - 'A' + 10;
            return -1;
        }

        boolean isNumChar(char c) {
            return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
        }

        void skipWs() {
            while (pos < src.length()) {
                char c = src.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        void expect(char c) {
            if (pos >= src.length() || src.charAt(pos) != c) {
                throw err("Expected '" + c + "'");
            }
            pos++;
        }

        char next() {
            if (pos >= src.length()) {
                throw err("Unexpected end of input");
            }
            return src.charAt(pos++);
        }

        char peek() {
            if (pos >= src.length()) {
                throw err("Unexpected end of input");
            }
            return src.charAt(pos);
        }

        IllegalArgumentException err(String msg) {
            return new IllegalArgumentException(msg + " at offset " + pos);
        }
    }
}
