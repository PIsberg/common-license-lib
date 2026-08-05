package se.deversity.common.license.lemonsqueezy;

import se.deversity.common.license.LicenseResult;
import se.deversity.common.license.LicenseResult.DeniedReason;
import se.deversity.common.license.internal.Json;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import se.deversity.vibetags.annotations.AIAudit;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AISecure;

/**
 * Online validator against LemonSqueezy's
 * <a href="https://docs.lemonsqueezy.com/api/license-api/validate-license-key">validate</a>
 * endpoint.
 *
 * <p><strong>The endpoint is unauthenticated and global.</strong> {@code POST /v1/licenses/validate}
 * carries no account credential — the license key is the only input — so it answers for
 * <em>every</em> license key issued by <em>every</em> LemonSqueezy store. A validator that trusted
 * {@code valid: true} alone would accept a key bought from an unrelated vendor. That is why
 * {@code storeId} is mandatory and is checked against {@code meta.store_id} on every success path;
 * {@code productId} narrows it further when a store sells more than one product.
 *
 * <p>Because LemonSqueezy has no server-side equivalent of Keygen's {@code meta.scope.email}, the
 * binding is done here against {@code meta.customer_email}. The default is
 * {@link EmailBinding#DOMAIN}: organisations buy once through a billing address and expect every
 * developer on that domain to be covered, so exact-address matching would deny everyone except the
 * person who paid. {@link EmailBinding#EXACT} is available for per-seat licensing.
 *
 * <p>Status handling follows LemonSqueezy's documented set: {@code inactive}, {@code active},
 * {@code expired}, {@code disabled}. Note that {@code inactive} means "valid, but never activated"
 * — this validator never calls {@code /activate}, so a freshly issued key is legitimately
 * {@code inactive}. Gating on {@code status == "active"} would reject every unused key.
 *
 * <p>Does <strong>not</strong> throw on HTTP/network errors — everything maps to a
 * {@link LicenseResult.Denied} with {@link DeniedReason#NETWORK_ERROR}. Fail-open is the caller's
 * decision, taken at the {@code LicenseGate} level.
 */
@AISecure(aspect = "license validation")
@AIAudit(checkFor = {"Authentication Bypass", "Credential leakage in exception messages"})
@AIContext(
    focus = "Fail closed, and never trust `valid:true` on its own. The validate endpoint is "
        + "unauthenticated and spans all of LemonSqueezy, so the store scope is the only thing "
        + "separating our customers' keys from every other vendor's. Keep the store check on every "
        + "success path.",
    avoids = "Jackson, Gson, org.json, OkHttp, Apache HttpClient — the library ships zero runtime dependencies"
)
public final class LemonSqueezyValidator {

    /** LemonSqueezy's License API host. */
    public static final URI DEFAULT_BASE_URI = URI.create("https://api.lemonsqueezy.com");

    /** How the buyer's address is matched against the address the software is running under. */
    public enum EmailBinding {
        /**
         * Any address on the buyer's email domain may use the licence. This is what makes a
         * company licence work: one purchase by {@code billing@acme.com} covers every developer
         * at {@code acme.com}, which is how organisations actually buy.
         */
        DOMAIN,
        /**
         * Only the exact address that bought the licence may use it — per-seat licensing. Stricter,
         * but a company that buys once through a billing address will find that none of its
         * developers can run.
         */
        EXACT
    }

    private final HttpClient http;
    private final URI baseUri;
    private final Duration timeout;
    private final Long storeId;
    private final Long productId;
    private final EmailBinding binding;

    /**
     * @param http      shared HTTP client (the caller owns lifecycle)
     * @param baseUri   typically {@link #DEFAULT_BASE_URI}; overridable for tests
     * @param timeout   per-request timeout
     * @param storeId   LemonSqueezy store ID that legitimately issues our keys. Required — see the
     *                  class javadoc for why omitting it would accept any vendor's key.
     * @param productId optional product ID; when non-null a key for another product of the same
     *                  store is rejected
     * @param binding   how the buyer's address is matched; {@code null} means
     *                  {@link EmailBinding#DOMAIN}
     */
    public LemonSqueezyValidator(HttpClient http, URI baseUri, Duration timeout,
                                 Long storeId, Long productId, EmailBinding binding) {
        this.http = Objects.requireNonNull(http, "http");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.storeId = Objects.requireNonNull(storeId,
            "storeId is required: /v1/licenses/validate is unauthenticated and global, so without "
                + "a store scope any LemonSqueezy license key would validate");
        this.productId = productId;
        this.binding = binding == null ? EmailBinding.DOMAIN : binding;
    }

    /**
     * Validate {@code licenseKey} and confirm it was issued by the configured store (and product,
     * when set) to {@code email}.
     */
    public LicenseResult validate(String licenseKey, String email) {
        String body = "license_key=" + URLEncoder.encode(licenseKey, StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve("/v1/licenses/validate"))
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();

        HttpResponse<String> resp;
        try {
            resp = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR, "IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR, "Interrupted");
        }

        return mapResponse(resp, email);
    }

    /**
     * Maps a validate response onto a {@link LicenseResult}. Package-private so the scope and
     * status branches can be exercised without a live endpoint.
     */
    LicenseResult mapResponse(HttpResponse<String> resp, String email) {
        int status = resp.statusCode();

        // 429 and 5xx say nothing about the key — treat as transport failure so a rate-limited
        // or broken LemonSqueezy cannot silently revoke a paying customer's license.
        if (status == 429 || status >= 500) {
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR, "LemonSqueezy HTTP " + status);
        }

        Object parsed;
        try {
            parsed = Json.parse(resp.body());
        } catch (IllegalArgumentException e) {
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR,
                "Malformed LemonSqueezy response: " + e.getMessage());
        }
        if (!(parsed instanceof Map<?, ?>)) {
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR,
                "Unexpected LemonSqueezy payload shape");
        }

        String keyStatus = lower(str(Json.get(parsed, "license_key", "status")));

        if (!Boolean.TRUE.equals(Json.get(parsed, "valid"))) {
            return denyForInvalid(status, keyStatus, str(Json.get(parsed, "error")));
        }

        // Defensive: `valid` should never be true alongside a terminal status, but the endpoint is
        // outside our control and a stale `valid` must not outrank an explicit expiry/disable.
        // `inactive` is deliberately absent — it means "issued, never activated", which is valid.
        if ("expired".equals(keyStatus)) {
            return LicenseResult.Denied.of(DeniedReason.LICENSE_EXPIRED);
        }
        if ("disabled".equals(keyStatus)) {
            return LicenseResult.Denied.of(DeniedReason.LICENSE_SUSPENDED);
        }

        return checkScope(parsed, email);
    }

    /** Confirms the key really belongs to our store, our product and this user. */
    private LicenseResult checkScope(Object parsed, String email) {
        Long respStore = asLong(Json.get(parsed, "meta", "store_id"));
        if (!storeId.equals(respStore)) {
            // Missing meta lands here too, which is the point: no store evidence, no pass.
            return new LicenseResult.Denied(DeniedReason.LICENSE_INVALID,
                "license key was issued by a different LemonSqueezy store");
        }

        if (productId != null) {
            Long respProduct = asLong(Json.get(parsed, "meta", "product_id"));
            if (!productId.equals(respProduct)) {
                return new LicenseResult.Denied(DeniedReason.LICENSE_INVALID,
                    "license key was issued for a different product");
            }
        }

        String customerEmail = str(Json.get(parsed, "meta", "customer_email"));
        if (!emailMatches(customerEmail, email)) {
            return new LicenseResult.Denied(DeniedReason.LICENSE_INVALID, binding == EmailBinding.DOMAIN
                ? "license key belongs to a different organisation"
                : "license key is registered to a different email address");
        }

        return new LicenseResult.Allowed(LicenseResult.AllowedReason.LICENSE_VALID);
    }

    /**
     * Compares the buyer's address with the running user's, under the configured binding.
     *
     * <p>{@link EmailBinding#DOMAIN} is what makes a company licence usable: one purchase by
     * {@code billing@acme.com} covers every developer at {@code acme.com}. Under
     * {@link EmailBinding#EXACT} only the buyer can run, which suits per-seat licensing.
     */
    private boolean emailMatches(String customerEmail, String userEmail) {
        if (customerEmail == null || userEmail == null) {
            return false;
        }
        String buyer = customerEmail.trim();
        String user = userEmail.trim();
        if (binding == EmailBinding.EXACT) {
            return buyer.equalsIgnoreCase(user);
        }
        String buyerDomain = domainOf(buyer);
        String userDomain = domainOf(user);
        // A blank domain on either side means an address we could not parse. Fail closed rather
        // than let two unparseable addresses "match" each other.
        return buyerDomain != null && buyerDomain.equals(userDomain);
    }

    /** Lower-cased domain part of an address, or {@code null} if it has no single {@code @}. */
    private static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        if (at < 1 || at == email.length() - 1) {
            return null;
        }
        String domain = lower(email.substring(at + 1));
        return domain.isBlank() ? null : domain;
    }

    /** Maps a {@code valid:false} (or 4xx) response onto the closest denial reason. */
    private static LicenseResult denyForInvalid(int httpStatus, String keyStatus, String error) {
        DeniedReason reason = switch (keyStatus == null ? "" : keyStatus) {
            case "expired"  -> DeniedReason.LICENSE_EXPIRED;
            case "disabled" -> DeniedReason.LICENSE_SUSPENDED;
            default -> httpStatus == 404 || containsNotFound(error)
                ? DeniedReason.LICENSE_NOT_FOUND
                : DeniedReason.LICENSE_INVALID;
        };
        // `error` is LemonSqueezy's own wording (e.g. "license_key not found") and never echoes
        // the submitted key; the key itself is deliberately absent from this message.
        return new LicenseResult.Denied(reason,
            error != null ? "LemonSqueezy: " + error : "LemonSqueezy HTTP " + httpStatus);
    }

    private static boolean containsNotFound(String error) {
        return error != null && lower(error).contains("not found");
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static Long asLong(Object o) {
        return o instanceof Number n ? n.longValue() : null;
    }
}
