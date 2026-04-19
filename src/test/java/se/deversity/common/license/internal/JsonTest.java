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
