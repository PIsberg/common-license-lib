package se.deversity.common.license.lemonsqueezy;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LemonSqueezyCheckoutTest {

    private final LemonSqueezyCheckout checkout = new LemonSqueezyCheckout("my-store");

    @Test
    void buildsBareUrlForAnonymousCheckout() {
        URI u = checkout.buildCheckoutUrl(null, "VAR123", null);
        assertEquals(URI.create("https://my-store.lemonsqueezy.com/buy/VAR123"), u);
    }

    @Test
    void encodesVariantIdAsAPathSegment() {
        // Form encoding would turn the space into '+', which in a path is a literal plus.
        URI u = checkout.buildCheckoutUrl(null, "VAR 1+2", null);
        assertEquals("/buy/VAR%201%2B2", u.getRawPath());
        assertEquals("/buy/VAR 1+2", u.getPath());
    }

    @Test
    void prefillsEmailUrlEncoded() {
        URI u = checkout.buildCheckoutUrl("ada@corp.com", "VAR123", null);
        assertTrue(u.toString().contains("checkout%5Bemail%5D=ada%40corp.com"),
            "got: " + u);
    }

    @Test
    void includesCustomFields() {
        URI u = checkout.buildCheckoutUrl("ada@corp.com", "VAR123",
            Map.of("plan", "pro", "src", "inapp-upgrade"));
        String s = u.toString();
        assertTrue(s.contains("checkout%5Bcustom%5D%5Bplan%5D=pro"),     "got: " + s);
        assertTrue(s.contains("checkout%5Bcustom%5D%5Bsrc%5D=inapp-upgrade"), "got: " + s);
    }

    @Test
    void rejectsSubdomainContainingDotsOrSlashes() {
        assertThrows(IllegalArgumentException.class,
            () -> new LemonSqueezyCheckout("my-store.lemonsqueezy.com"));
        assertThrows(IllegalArgumentException.class,
            () -> new LemonSqueezyCheckout("foo/bar"));
    }

    @Test
    void rejectsSubdomainThatMovesTheUrlOffLemonSqueezy() {
        // Only '.' and '/' were rejected. "localhost#" built https://localhost#.lemonsqueezy.com/...,
        // whose host is "localhost"; '?', '@', ':' and '\' end or split the authority the same way,
        // and "2130706433#" (a dot-free spelling of 127.0.0.1) needs no dot to reach an IP.
        for (String bad : new String[] {"localhost#", "x?", "user@", "x:8080", "a\\b", "my store", ""}) {
            assertThrows(IllegalArgumentException.class, () -> new LemonSqueezyCheckout(bad), bad);
        }
        URI u = new LemonSqueezyCheckout("My-Store-2").buildCheckoutUrl(null, "1", null);
        assertEquals("my-store-2.lemonsqueezy.com", u.getHost().toLowerCase(java.util.Locale.ROOT));
    }
}
