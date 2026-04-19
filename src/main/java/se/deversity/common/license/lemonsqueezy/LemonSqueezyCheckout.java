package se.deversity.common.license.lemonsqueezy;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Build pre-filled checkout URLs for LemonSqueezy's hosted storefront.
 *
 * <p>The URL shape is {@code https://<store>.lemonsqueezy.com/buy/<variantId>?checkout[email]=...}.
 * See <a href="https://docs.lemonsqueezy.com/help/online-store/prefilling-checkout-fields">docs</a>.
 *
 * <p>This class does not hit the LS API — it's a pure URL builder, safe to call from any thread.
 */
public final class LemonSqueezyCheckout {

    private final String storeSubdomain;

    /**
     * @param storeSubdomain your LS store's subdomain (the {@code my-store} in
     *                       {@code https://my-store.lemonsqueezy.com/}). Must not be {@code null}.
     */
    public LemonSqueezyCheckout(String storeSubdomain) {
        Objects.requireNonNull(storeSubdomain, "storeSubdomain");
        if (storeSubdomain.isBlank() || storeSubdomain.contains("/") || storeSubdomain.contains(".")) {
            throw new IllegalArgumentException(
                "storeSubdomain must be the bare subdomain (no dots, no slashes): " + storeSubdomain);
        }
        this.storeSubdomain = storeSubdomain;
    }

    /**
     * Build a checkout URL, pre-filling {@code email} and any additional
     * {@code checkout[custom][key]} entries from {@code customData} (may be {@code null}).
     *
     * @param email     email to pre-fill
     * @param variantId LemonSqueezy variant ID — the target product/variant to sell
     * @param customData optional map of custom fields to pass through to the webhook
     */
    public URI buildCheckoutUrl(String email, String variantId, Map<String, String> customData) {
        Objects.requireNonNull(variantId, "variantId");
        StringJoiner qs = new StringJoiner("&");
        if (email != null && !email.isBlank()) {
            qs.add("checkout%5Bemail%5D=" + encode(email));
        }
        if (customData != null) {
            for (Map.Entry<String, String> e : customData.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                qs.add("checkout%5Bcustom%5D%5B" + encode(e.getKey()) + "%5D="
                    + encode(e.getValue()));
            }
        }
        String base = "https://" + storeSubdomain + ".lemonsqueezy.com/buy/" + encode(variantId);
        return URI.create(qs.length() == 0 ? base : base + "?" + qs);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
