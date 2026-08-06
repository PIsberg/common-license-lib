package se.deversity.common.license.paddle;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Build pre-filled checkout URLs for Paddle Billing's hosted checkout.
 *
 * <p>The URL shape is {@code https://pay.paddle.io/checkout/<hostedCheckoutId>?price_id=...&user_email=...}.
 * The hosted checkout id ({@code hsc_...}) comes from a checkout link created in the Paddle
 * dashboard. See
 * <a href="https://developer.paddle.com/paddlejs/hosted-checkout-url-parameters">Hosted checkout
 * URL query parameters</a>.
 *
 * <p>This class does not hit the Paddle API — it's a pure URL builder, safe to call from any thread.
 */
public final class PaddleCheckout {

    private static final String BASE = "https://pay.paddle.io/checkout/";

    private final String hostedCheckoutId;

    /**
     * @param hostedCheckoutId the hosted checkout id from the Paddle dashboard's checkout link,
     *                         always prefixed {@code hsc_}. Must not be {@code null}.
     */
    public PaddleCheckout(String hostedCheckoutId) {
        Objects.requireNonNull(hostedCheckoutId, "hostedCheckoutId");
        if (!hostedCheckoutId.startsWith("hsc_")
            || hostedCheckoutId.contains("/") || hostedCheckoutId.contains("?")
            || hostedCheckoutId.contains("&") || hostedCheckoutId.contains("#")) {
            throw new IllegalArgumentException(
                "hostedCheckoutId must be a bare hsc_-prefixed id (no separators): " + hostedCheckoutId);
        }
        this.hostedCheckoutId = hostedCheckoutId;
    }

    /**
     * Build a checkout URL pre-filling {@code email} and selecting {@code priceId}.
     * Both may be {@code null}: a null {@code priceId} sells the hosted checkout's default
     * prices, a null {@code email} leaves the field for the customer to fill.
     */
    public URI buildCheckoutUrl(String email, String priceId) {
        return buildCheckoutUrl(email, priceId, null);
    }

    /**
     * Build a checkout URL with additional query parameters (for example {@code discount_code}
     * or {@code locale}). Keys and values are percent-encoded; {@code extraParams} may be
     * {@code null}. {@code price_id} and {@code user_email} must be passed through their
     * dedicated arguments, not repeated here.
     */
    public URI buildCheckoutUrl(String email, String priceId, Map<String, String> extraParams) {
        StringJoiner qs = new StringJoiner("&");
        if (priceId != null && !priceId.isBlank()) {
            qs.add("price_id=" + encode(priceId));
        }
        if (email != null && !email.isBlank()) {
            qs.add("user_email=" + encode(email));
        }
        if (extraParams != null) {
            for (Map.Entry<String, String> e : extraParams.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String key = e.getKey();
                if (key.equals("price_id") || key.equals("user_email")) {
                    throw new IllegalArgumentException(
                        "pass " + key + " through its dedicated argument, not extraParams");
                }
                qs.add(encode(key) + "=" + encode(e.getValue()));
            }
        }
        String base = BASE + hostedCheckoutId;
        return URI.create(qs.length() == 0 ? base : base + "?" + qs);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
