package se.deversity.common.license;

import se.deversity.common.license.email.EmailClassification;
import se.deversity.common.license.keygen.KeygenValidator;
import se.deversity.common.license.lemonsqueezy.LemonSqueezyCheckout;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Map;
import java.util.Objects;

/**
 * Primary entrypoint. Instantiate once per consumer app, reuse across calls.
 *
 * <pre>{@code
 * LicenseGate gate = LicenseGate.of(config);
 * LicenseResult r = gate.check(userEmail, licenseKey);
 * switch (r) {
 *     case LicenseResult.Allowed a -> startApp();
 *     case LicenseResult.Denied  d -> showPaywall(d.reason(), gate.checkoutUrl(userEmail, variantId));
 * }
 * }</pre>
 *
 * <p>Thread-safe: holds only immutable config + a shared {@link HttpClient}.
 */
public final class LicenseGate {

    private final LicenseConfig config;
    private final HttpClient http;
    private final KeygenValidator keygen;
    private final LemonSqueezyCheckout checkout;

    private LicenseGate(LicenseConfig config) {
        this.config = config;
        this.http = config.httpClient() != null ? config.httpClient() : HttpClient.newHttpClient();
        this.keygen = new KeygenValidator(
            http,
            config.keygenAccountId(),
            config.keygenApiKey(),
            config.keygenBaseUri(),
            config.keygenTimeout());
        this.checkout = config.lemonSqueezyStoreSubdomain() != null
            ? new LemonSqueezyCheckout(config.lemonSqueezyStoreSubdomain())
            : null;
    }

    /** Factory. */
    public static LicenseGate of(LicenseConfig config) {
        Objects.requireNonNull(config, "config");
        return new LicenseGate(config);
    }

    /**
     * Check whether {@code email} / {@code licenseKey} may use the app right now.
     *
     * @param email       user's email address — required
     * @param licenseKey  license key from Keygen; may be {@code null} (free-email users don't need one)
     */
    public LicenseResult check(String email, String licenseKey) {
        if (config.mockMode()) {
            return new LicenseResult.Allowed(LicenseResult.AllowedReason.MOCKED_ALLOWED);
        }
        EmailClassification cls = config.emailClassifier().classify(email);
        return switch (cls) {
            case INVALID -> LicenseResult.Denied.of(LicenseResult.DeniedReason.INVALID_EMAIL);
            case FREE_PROVIDER -> new LicenseResult.Allowed(LicenseResult.AllowedReason.FREE_PROVIDER_EMAIL);
            case COMMERCIAL -> checkCommercial(email, licenseKey);
        };
    }

    private LicenseResult checkCommercial(String email, String licenseKey) {
        if (licenseKey == null || licenseKey.isBlank()) {
            return LicenseResult.Denied.of(LicenseResult.DeniedReason.LICENSE_REQUIRED);
        }
        LicenseResult r = keygen.validate(licenseKey, email);
        if (r instanceof LicenseResult.Denied d
            && d.reason() == LicenseResult.DeniedReason.NETWORK_ERROR
            && config.allowOnNetworkError()) {
            return new LicenseResult.Allowed(LicenseResult.AllowedReason.NETWORK_ERROR_ALLOWED);
        }
        return r;
    }

    /**
     * Build a LemonSqueezy pre-filled checkout URL.
     *
     * @throws LicenseException if the LemonSqueezy store subdomain was not configured
     */
    public URI checkoutUrl(String email, String variantId) {
        return checkoutUrl(email, variantId, null);
    }

    /** Checkout URL with optional {@code checkout[custom][...]} fields. */
    public URI checkoutUrl(String email, String variantId, Map<String, String> customData) {
        if (checkout == null) {
            throw new LicenseException(
                "LemonSqueezy store subdomain not configured — cannot build a checkout URL");
        }
        return checkout.buildCheckoutUrl(email, variantId, customData);
    }

    /** The effective config (useful for diagnostics; credentials are redacted in {@code toString}). */
    public LicenseConfig config() {
        return config;
    }
}
