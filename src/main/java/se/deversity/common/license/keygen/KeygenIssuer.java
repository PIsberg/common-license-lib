package se.deversity.common.license.keygen;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

import se.deversity.common.license.LicenseException;
import se.deversity.common.license.internal.Json;

import se.deversity.vibetags.annotations.AIAudit;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AISecure;

/**
 * Operator-side issuance of Keygen licenses: ensure a user exists for the buyer's email, then
 * create a license owned by that user under a policy. This is the fulfilment half of selling
 * through a commerce provider with no license engine of its own (Paddle Billing); validation of
 * the issued key stays with {@link KeygenValidator}.
 *
 * <p>Runs with an <strong>admin or product token</strong> and therefore belongs on the vendor's
 * machine or backend only — never ship this token to end users. See
 * <a href="https://keygen.sh/docs/api/licenses/">Licenses API</a> and
 * <a href="https://keygen.sh/docs/api/users/">Users API</a>.
 *
 * <p>Unlike the validators this class throws on failure ({@link LicenseException}): issuance is
 * an operator action where a loud failure is the safe outcome, not a gate that must fail closed.
 */
@AISecure(aspect = "license issuance (admin-token API access)")
@AIAudit(checkFor = {"Credential leakage in exception messages", "Server-side request forgery via caller-supplied ids"})
@AIContext(
    focus = "Operator-side only: the admin token must never appear in exception messages, "
        + "toString or logs. Caller-supplied ids and emails are URL-encoded or JSON-escaped "
        + "before use. Failures throw LicenseException with the HTTP status and Keygen's "
        + "error title only.",
    avoids = "Jackson, Gson, org.json, OkHttp, Apache HttpClient — the library ships zero runtime dependencies"
)
public final class KeygenIssuer {

    private final HttpClient http;
    private final String accountId;
    private final String adminToken;
    private final URI baseUri;
    private final Duration timeout;

    /**
     * @param http       shared HTTP client (the caller owns lifecycle)
     * @param accountId  Keygen account UUID or slug
     * @param adminToken admin or product token (passed as a Bearer token)
     * @param baseUri    typically {@code https://api.keygen.sh}
     * @param timeout    per-request timeout
     */
    public KeygenIssuer(HttpClient http, String accountId, String adminToken,
                        URI baseUri, Duration timeout) {
        this.http = Objects.requireNonNull(http, "http");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.adminToken = Objects.requireNonNull(adminToken, "adminToken");
        this.baseUri = Objects.requireNonNull(baseUri, "baseUri");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    /**
     * Ensure a Keygen user exists for {@code email} and return its id. Creates the user when
     * absent; when Keygen rejects the create as a duplicate, falls back to fetching the existing
     * user by email (Keygen accepts an email anywhere a user id is expected).
     */
    public String ensureUser(String email) {
        Objects.requireNonNull(email, "email");
        String body = "{\"data\":{\"type\":\"users\",\"attributes\":{\"email\":\""
            + Json.escape(email) + "\"}}}";
        HttpResponse<String> resp = send(post("/users", body));
        if (resp.statusCode() == 201) {
            return idOf(resp, "user create");
        }
        // 409/422: already exists (exact code differs by account setup) — fetch by email instead.
        if (resp.statusCode() == 409 || resp.statusCode() == 422) {
            HttpResponse<String> get = send(get("/users/" + encodePath(email)));
            if (get.statusCode() == 200) {
                return idOf(get, "user lookup");
            }
            throw failure("user lookup", get);
        }
        throw failure("user create", resp);
    }

    /**
     * Create a license under {@code policyId} owned by user {@code ownerUserId} and return the
     * generated license key.
     */
    public String createLicense(String policyId, String ownerUserId) {
        Objects.requireNonNull(policyId, "policyId");
        Objects.requireNonNull(ownerUserId, "ownerUserId");
        String body = "{\"data\":{\"type\":\"licenses\",\"relationships\":{"
            + "\"policy\":{\"data\":{\"type\":\"policies\",\"id\":\"" + Json.escape(policyId) + "\"}},"
            + "\"owner\":{\"data\":{\"type\":\"users\",\"id\":\"" + Json.escape(ownerUserId) + "\"}}"
            + "}}}";
        HttpResponse<String> resp = send(post("/licenses", body));
        if (resp.statusCode() != 201) {
            throw failure("license create", resp);
        }
        Object key = Json.get(parse(resp, "license create"), "data", "attributes", "key");
        if (key == null) {
            throw new LicenseException("Keygen license create: response carries no key");
        }
        return key.toString();
    }

    /**
     * Convenience for the common fulfilment flow: {@link #ensureUser(String)} then
     * {@link #createLicense(String, String)}.
     *
     * @return the generated license key
     */
    public String issueLicense(String policyId, String ownerEmail) {
        return createLicense(policyId, ensureUser(ownerEmail));
    }

    private HttpRequest post(String path, String body) {
        return builder(path)
            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
            .build();
    }

    private HttpRequest get(String path) {
        return builder(path).GET().build();
    }

    private HttpRequest.Builder builder(String path) {
        return HttpRequest.newBuilder(baseUri.resolve("/v1/accounts/" + accountId + path))
            .timeout(timeout)
            .header("Authorization", "Bearer " + adminToken)
            .header("Accept", "application/vnd.api+json")
            .header("Content-Type", "application/vnd.api+json");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new LicenseException("Keygen request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LicenseException("Keygen request interrupted", e);
        }
    }

    private static Object parse(HttpResponse<String> resp, String what) {
        try {
            return Json.parse(resp.body());
        } catch (IllegalArgumentException e) {
            throw new LicenseException("Keygen " + what + ": malformed response");
        }
    }

    private static String idOf(HttpResponse<String> resp, String what) {
        Object id = Json.get(parse(resp, what), "data", "id");
        if (id == null) {
            throw new LicenseException("Keygen " + what + ": response carries no id");
        }
        return id.toString();
    }

    /**
     * Build the failure exception from the response's status and Keygen's error title only —
     * never the raw body, which can echo request contents.
     */
    private static LicenseException failure(String what, HttpResponse<String> resp) {
        String title = "";
        try {
            Object errors = Json.get(Json.parse(resp.body()), "errors");
            if (errors instanceof java.util.List<?> list && !list.isEmpty()) {
                Object t = Json.get(list.get(0), "title");
                if (t != null) {
                    title = ": " + t;
                }
            }
        } catch (RuntimeException ignored) {
            // Malformed error body; the status code is still the important part.
        }
        return new LicenseException("Keygen " + what + " failed, HTTP " + resp.statusCode() + title);
    }

    private static String encodePath(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
