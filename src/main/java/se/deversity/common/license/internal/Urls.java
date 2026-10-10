package se.deversity.common.license.internal;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * URL encoding for path segments.
 *
 * <p>{@link URLEncoder} implements form encoding, where a space becomes {@code +}. That is right
 * for a query string and wrong for a path, where {@code +} is a literal plus: a value with a space
 * would be sent as a different value.
 *
 * <p><strong>This is an internal API.</strong> Not for consumer use.
 */
public final class Urls {

    private Urls() {
    }

    /**
     * Percent-encode {@code s} as a single URL path segment (UTF-8). A space becomes {@code %20},
     * and {@code +}, {@code /} and {@code @} are escaped.
     */
    public static String encodePathSegment(String s) {
        // URLEncoder already escapes a literal '+' as %2B, so every '+' left is a space.
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
