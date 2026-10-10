package se.deversity.common.license.internal;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void parsesTheKeygenResponseShapeWeCareAbout() {
        String body = """
            {
              "data": {
                "type": "licenses",
                "id": "abc",
                "attributes": { "expiry": "2099-01-01T00:00:00Z" }
              },
              "meta": {
                "valid": true,
                "code": "VALID",
                "detail": "is valid"
              }
            }""";

        Object root = Json.parse(body);
        assertEquals(Boolean.TRUE, Json.get(root, "meta", "valid"));
        assertEquals("VALID",     Json.get(root, "meta", "code"));
        assertEquals("abc",       Json.get(root, "data", "id"));
    }

    @Test
    void parsesPrimitivesAndArrays() {
        assertEquals(42L,            Json.parse("42"));
        assertEquals(3.14,          (Double) Json.parse("3.14"), 1e-9);
        assertEquals("hi",           Json.parse("\"hi\""));
        assertEquals(Boolean.TRUE,   Json.parse("true"));
        assertNull(                  Json.parse("null"));
        assertEquals(List.of(1L, 2L, 3L), Json.parse("[1,2,3]"));
    }

    @Test
    void decodesUnicodeEscapes() {
        assertEquals("\u00e4",       Json.parse("\"\\u00e4\""));
    }

    @Test
    void rejectsSignedOrNonHexUnicodeEscapes() {
        // Integer.parseInt(.., 16) accepts a leading sign, so "\-001" decoded to U+FFFF and
        // "\+041" to 'A' instead of being rejected as the malformed escapes they are.
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\u+041\""));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\u-001\""));
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"\\uzzzz\""));
        assertEquals("A", Json.parse("\"\\u0041\""));
    }

    @Test
    void deepNestingIsRejectedAsMalformedInsteadOfOverflowingTheStack() {
        // The parser recurses per nesting level. Without a bound, a response body of a few
        // hundred kilobytes of '[' threw StackOverflowError, an Error no validator catches, so
        // check() threw instead of failing closed with Denied(NETWORK_ERROR).
        String deep = "[".repeat(200_000) + "]".repeat(200_000);
        assertThrows(IllegalArgumentException.class, () -> Json.parse(deep));

        String fine = "[".repeat(64) + "]".repeat(64);
        assertNotNull(Json.parse(fine));
    }

    @Test
    void rejectsTrailingContent() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("1 2"));
    }

    @Test
    void getReturnsNullForMissingPathSegments() {
        Object root = Json.parse("{\"a\":{\"b\":1}}");
        assertNull(Json.get(root, "a", "c"));
        assertNull(Json.get(root, "x", "y", "z"));
    }

    @Test
    void escapeHandlesControlCharsAndQuotes() {
        String out = Json.escape("a\"b\\c\n");
        assertEquals("a\\\"b\\\\c\\n", out);
        assertTrue(Json.escape("\u0001").startsWith("\\u"));
    }

    @Test
    void roundTripsKeygenBodyShape() {
        Object parsed = Json.parse("{\"meta\":{\"key\":\"" + Json.escape("abc\"def") + "\"}}");
        assertEquals("abc\"def", Map.class.cast(
            Map.class.cast(((Map<?, ?>) parsed).get("meta"))
        ).get("key"));
    }
}
