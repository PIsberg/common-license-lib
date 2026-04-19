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
}
