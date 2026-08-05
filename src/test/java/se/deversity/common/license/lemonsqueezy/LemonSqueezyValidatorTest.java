package se.deversity.common.license.lemonsqueezy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.deversity.common.license.LicenseResult;
import se.deversity.common.license.LicenseResult.AllowedReason;
import se.deversity.common.license.LicenseResult.DeniedReason;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the validator against a real loopback HTTP server so the form-encoded request and the
 * JSON response shape are exercised end-to-end, matching {@code KeygenValidatorTest}.
 *
 * <p>The response fixtures follow the documented shape of
 * {@code POST /v1/licenses/validate}.
 */
class LemonSqueezyValidatorTest {

    private static final long OUR_STORE = 42L;
    private static final long OUR_PRODUCT = 7L;

    private HttpServer server;
    private URI baseUri;
    private HttpClient http;

    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private volatile int responseStatus;
    private volatile String responseBody;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        http = HttpClient.newHttpClient();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            lastPath.set(ex.getRequestURI().getPath());
            lastContentType.set(ex.getRequestHeaders().getFirst("Content-Type"));
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));

            byte[] body = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    /** Default binding is DOMAIN — the company-licence case. */
    private LemonSqueezyValidator newValidator() {
        return new LemonSqueezyValidator(http, baseUri, Duration.ofSeconds(3), OUR_STORE, null, null);
    }

    private LemonSqueezyValidator exactBinding() {
        return new LemonSqueezyValidator(http, baseUri, Duration.ofSeconds(3), OUR_STORE, null,
            LemonSqueezyValidator.EmailBinding.EXACT);
    }

    private LemonSqueezyValidator scopedToProduct() {
        return new LemonSqueezyValidator(http, baseUri, Duration.ofSeconds(3), OUR_STORE, OUR_PRODUCT,
            null);
    }

    /** A successful validate payload, parameterised on the bits the scope checks read. */
    private static String validPayload(String status, long storeId, long productId, String email) {
        return "{\"valid\":true,\"error\":null,"
            + "\"license_key\":{\"id\":1,\"status\":\"" + status + "\",\"key\":\"K\","
            + "\"activation_limit\":1,\"activation_usage\":0},"
            + "\"instance\":null,"
            + "\"meta\":{\"store_id\":" + storeId + ",\"order_id\":2,\"product_id\":" + productId
            + ",\"product_name\":\"Lib\",\"variant_id\":5,\"customer_id\":6,"
            + "\"customer_name\":\"Ada\",\"customer_email\":\"" + email + "\"}}";
    }

    @Test
    void postsFormEncodedKeyToTheValidateEndpointWithoutCredentials() {
        responseStatus = 200;
        responseBody = validPayload("inactive", OUR_STORE, OUR_PRODUCT, "ada@corp.com");

        newValidator().validate("KEY 123", "ada@corp.com");

        assertEquals("/v1/licenses/validate", lastPath.get());
        assertEquals("application/x-www-form-urlencoded", lastContentType.get());
        assertEquals("license_key=KEY+123", lastBody.get());
        assertNull(lastAuth.get(), "validate is unauthenticated; sending a token would be a leak");
    }

    @Test
    void neverActivatedKeyIsValid() {
        // `inactive` means issued-but-never-activated. This validator never calls /activate, so
        // gating on status == "active" would reject every legitimately unused key.
        responseStatus = 200;
        responseBody = validPayload("inactive", OUR_STORE, OUR_PRODUCT, "ada@corp.com");

        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");

        assertInstanceOf(LicenseResult.Allowed.class, r);
        assertEquals(AllowedReason.LICENSE_VALID, ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void activeKeyIsValid() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "ada@corp.com");
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(AllowedReason.LICENSE_VALID, ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void emailMatchIsCaseAndWhitespaceInsensitive() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "Ada@Corp.com");
        LicenseResult r = newValidator().validate("KEY", "  ada@corp.COM ");
        assertInstanceOf(LicenseResult.Allowed.class, r);
    }

    // --- the reason storeId is mandatory -------------------------------------------------

    @Test
    void keyFromAnotherStoreIsRejectedEvenThoughLemonSqueezySaysValid() {
        // The endpoint is unauthenticated and global: a key bought from an unrelated vendor
        // genuinely returns valid:true. The store scope is the only thing standing in the way.
        responseStatus = 200;
        responseBody = validPayload("active", 999L, OUR_PRODUCT, "ada@corp.com");

        LicenseResult r = newValidator().validate("SOMEONE-ELSES-KEY", "ada@corp.com");

        assertInstanceOf(LicenseResult.Denied.class, r);
        assertEquals(DeniedReason.LICENSE_INVALID, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void missingStoreMetadataIsRejected() {
        responseStatus = 200;
        responseBody = "{\"valid\":true,\"error\":null,\"license_key\":{\"status\":\"active\"},\"meta\":{}}";

        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");

        assertEquals(DeniedReason.LICENSE_INVALID, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void keyForAnotherProductOfTheSameStoreIsRejectedWhenProductScoped() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, 8L, "ada@corp.com");

        LicenseResult r = scopedToProduct().validate("KEY", "ada@corp.com");

        assertEquals(DeniedReason.LICENSE_INVALID, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void otherProductIsAcceptedWhenNoProductScopeIsConfigured() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, 8L, "ada@corp.com");
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertInstanceOf(LicenseResult.Allowed.class, r);
    }

    // --- email binding: the company-licence case -----------------------------------------

    @Test
    void colleagueOnTheBuyersDomainMayUseTheCompanyLicence() {
        // The whole point of DOMAIN binding. A company buys once through billing@ and every
        // developer on that domain is covered; exact matching would deny all of them.
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "billing@acme-corp.com");

        LicenseResult r = newValidator().validate("KEY", "alice@acme-corp.com");

        assertInstanceOf(LicenseResult.Allowed.class, r);
        assertEquals(AllowedReason.LICENSE_VALID, ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void differentDomainIsRejectedUnderDomainBinding() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "billing@acme-corp.com");

        LicenseResult r = newValidator().validate("KEY", "mallory@other-corp.com");

        assertEquals(DeniedReason.LICENSE_INVALID, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void subdomainIsNotTheSameOrganisation() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "billing@acme-corp.com");

        LicenseResult r = newValidator().validate("KEY", "eve@evil.acme-corp.com.attacker.net");

        assertEquals(DeniedReason.LICENSE_INVALID, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void colleagueIsRejectedUnderExactBinding() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "billing@acme-corp.com");

        LicenseResult r = exactBinding().validate("KEY", "alice@acme-corp.com");

        assertEquals(DeniedReason.LICENSE_INVALID, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void buyerThemselvesIsAcceptedUnderExactBinding() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "billing@acme-corp.com");
        LicenseResult r = exactBinding().validate("KEY", "billing@acme-corp.com");
        assertInstanceOf(LicenseResult.Allowed.class, r);
    }

    @Test
    void unparseableAddressesDoNotMatchEachOther() {
        responseStatus = 200;
        responseBody = validPayload("active", OUR_STORE, OUR_PRODUCT, "not-an-email");

        LicenseResult r = newValidator().validate("KEY", "also-not-an-email");

        assertEquals(DeniedReason.LICENSE_INVALID, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void storeIdIsRequiredAtConstruction() {
        assertThrows(NullPointerException.class,
            () -> new LemonSqueezyValidator(http, baseUri, Duration.ofSeconds(3), null, null, null));
    }

    // --- status and error mapping --------------------------------------------------------

    @Test
    void mapsExpiredStatus() {
        responseStatus = 400;
        responseBody = "{\"valid\":false,\"error\":\"license_key has expired\","
            + "\"license_key\":{\"status\":\"expired\"}}";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.LICENSE_EXPIRED, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsDisabledStatusToSuspended() {
        responseStatus = 400;
        responseBody = "{\"valid\":false,\"error\":\"license_key is disabled\","
            + "\"license_key\":{\"status\":\"disabled\"}}";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.LICENSE_SUSPENDED, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsNotFoundStatusToLicenseNotFound() {
        responseStatus = 404;
        responseBody = "{\"valid\":false,\"error\":\"license_key not found\"}";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.LICENSE_NOT_FOUND, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsNotFoundWordingOnA400ToLicenseNotFound() {
        responseStatus = 400;
        responseBody = "{\"valid\":false,\"error\":\"license_key not found\"}";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.LICENSE_NOT_FOUND, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void expiredStatusOutranksAStaleValidFlag() {
        responseStatus = 200;
        responseBody = "{\"valid\":true,\"error\":null,\"license_key\":{\"status\":\"expired\"},"
            + "\"meta\":{\"store_id\":" + OUR_STORE + ",\"customer_email\":\"ada@corp.com\"}}";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.LICENSE_EXPIRED, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsRateLimitToNetworkErrorRatherThanRevokingTheLicense() {
        responseStatus = 429;
        responseBody = "{\"error\":\"Too many requests\"}";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.NETWORK_ERROR, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsServerErrorToNetworkError() {
        responseStatus = 503;
        responseBody = "{}";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.NETWORK_ERROR, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsMalformedJsonToNetworkError() {
        responseStatus = 200;
        responseBody = "not-json";
        LicenseResult r = newValidator().validate("KEY", "ada@corp.com");
        assertEquals(DeniedReason.NETWORK_ERROR, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void deniedMessagesNeverEchoTheSubmittedKey() {
        responseStatus = 404;
        responseBody = "{\"valid\":false,\"error\":\"license_key not found\"}";
        LicenseResult r = newValidator().validate("SUPER-SECRET-KEY", "ada@corp.com");
        String message = ((LicenseResult.Denied) r).message();
        assertNotNull(message);
        assertFalse(message.contains("SUPER-SECRET-KEY"), message);
    }
}
