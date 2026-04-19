package se.deversity.common.license.keygen;

import se.deversity.common.license.LicenseResult;
import se.deversity.common.license.LicenseResult.DeniedReason;
import se.deversity.common.license.internal.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Thin online validator against Keygen's
 * <a href="https://keygen.sh/docs/api/licenses/#licenses-actions-validate-key">validate-key</a>
 * action.
 *
 * <p>Binds the check to the caller's email via {@code meta.scope.email}, so a license
 * can be issued per-user by the consumer's Keygen account.
 *
 * <p>Does <strong>not</strong> throw on HTTP/network errors — maps everything to a
 * {@link LicenseResult.Denied} with {@link DeniedReason#NETWORK_ERROR}. Consumers
 * that want fail-open must opt in at the {@code LicenseGate} level.
 */
public final class KeygenValidator {

    private final HttpClient http;
    private final String accountId;
    private final String apiKey;
    private final URI baseUri;
    private final Duration timeout;

    /**
     * @param http       shared HTTP client (the caller owns lifecycle)
     * @param accountId  Keygen account UUID or slug
     * @param apiKey     admin or product token (passed as a Bearer token)
     * @param baseUri    typically {@code https://api.keygen.sh}
     * @param timeout    per-request timeout
     */
    public KeygenValidator(HttpClient http, String accountId, String apiKey, URI baseUri, Duration timeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Validate {@code licenseKey}, scoped to {@code email}. Returns the corresponding
     * {@link LicenseResult.Allowed} / {@link LicenseResult.Denied}.
     */
    public LicenseResult validate(String licenseKey, String email) {
        String body = "{\"meta\":{\"key\":\"" + Json.escape(licenseKey) + "\""
            + ",\"scope\":{\"email\":\"" + Json.escape(email) + "\"}}}";

        URI uri = baseUri.resolve("/v1/accounts/" + accountId
            + "/licenses/actions/validate-key");

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(timeout)
            .header("Authorization", "Bearer " + apiKey)
            .header("Accept", "application/vnd.api+json")
            .header("Content-Type", "application/vnd.api+json")
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

        return mapResponse(resp);
    }

    @SuppressWarnings("unchecked")
    static LicenseResult mapResponse(HttpResponse<String> resp) {
        int status = resp.statusCode();

        // Keygen returns 200 even for invalid keys — the outcome is in meta.valid / meta.code.
        // But 401/403 (bad API token) and 5xx should surface as NETWORK_ERROR to avoid
        // accidentally letting users through due to mis-configured credentials.
        if (status == 404) {
            return LicenseResult.Denied.of(DeniedReason.LICENSE_NOT_FOUND);
        }
        if (status >= 500 || status == 401 || status == 403 || status == 429) {
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR,
                "Keygen HTTP " + status);
        }

        Object parsed;
        try {
            parsed = Json.parse(resp.body());
        } catch (IllegalArgumentException e) {
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR,
                "Malformed Keygen response: " + e.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> m)) {
            return new LicenseResult.Denied(DeniedReason.NETWORK_ERROR,
                "Unexpected Keygen payload shape");
        }

        Object validObj = Json.get(parsed, "meta", "valid");
        Object codeObj  = Json.get(parsed, "meta", "code");
        String code = codeObj == null ? "" : codeObj.toString();

        if (Boolean.TRUE.equals(validObj)) {
            return new LicenseResult.Allowed(LicenseResult.AllowedReason.LICENSE_VALID);
        }

        DeniedReason reason = switch (code) {
            case "NOT_FOUND"              -> DeniedReason.LICENSE_NOT_FOUND;
            case "EXPIRED"                -> DeniedReason.LICENSE_EXPIRED;
            case "SUSPENDED", "BANNED"    -> DeniedReason.LICENSE_SUSPENDED;
            case ""                       -> DeniedReason.LICENSE_INVALID;
            default                       -> DeniedReason.LICENSE_INVALID;
        };
        return new LicenseResult.Denied(reason, "Keygen code=" + code);
    }
}
