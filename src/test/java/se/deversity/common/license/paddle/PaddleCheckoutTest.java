package se.deversity.common.license.paddle;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaddleCheckoutTest {

    private final PaddleCheckout checkout = new PaddleCheckout("hsc_01abc_secret");

    @Test
    void buildsBareUrlForDefaultPricesAnonymousCheckout() {
        URI u = checkout.buildCheckoutUrl(null, null);
        assertEquals(URI.create("https://pay.paddle.io/checkout/hsc_01abc_secret"), u);
    }

    @Test
    void selectsPriceAndPrefillsEmailUrlEncoded() {
        URI u = checkout.buildCheckoutUrl("ada@corp.com", "pri_01xyz");
        assertEquals(
            "https://pay.paddle.io/checkout/hsc_01abc_secret?price_id=pri_01xyz&user_email=ada%40corp.com",
            u.toString());
    }

    @Test
    void appendsExtraParamsEncoded() {
        URI u = checkout.buildCheckoutUrl(null, "pri_01xyz",
            Map.of("discount_code", "LAUNCH 20"));
        String s = u.toString();
        assertTrue(s.contains("price_id=pri_01xyz"), "got: " + s);
        assertTrue(s.contains("discount_code=LAUNCH+20"), "got: " + s);
    }

    @Test
    void rejectsPriceIdOrEmailSmuggledThroughExtraParams() {
        assertThrows(IllegalArgumentException.class,
            () -> checkout.buildCheckoutUrl(null, null, Map.of("price_id", "pri_1")));
        assertThrows(IllegalArgumentException.class,
            () -> checkout.buildCheckoutUrl(null, null, Map.of("user_email", "x@y.z")));
    }

    @Test
    void rejectsMalformedHostedCheckoutIds() {
        assertThrows(IllegalArgumentException.class, () -> new PaddleCheckout("01abc"));
        assertThrows(IllegalArgumentException.class, () -> new PaddleCheckout("hsc_01abc/evil"));
        assertThrows(IllegalArgumentException.class, () -> new PaddleCheckout("hsc_01abc?x=1"));
        assertThrows(IllegalArgumentException.class, () -> new PaddleCheckout("hsc_01abc#frag"));
        assertThrows(NullPointerException.class, () -> new PaddleCheckout(null));
    }
}
