package se.deversity.common.license;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate-level behaviour when {@link LicenseConfig.Provider#LEMONSQUEEZY} is selected: the request
 * must reach the LemonSqueezy endpoint rather than Keygen, and the free-email / missing-key
 * short-circuits must behave exactly as they do on the Keygen path.
 */
class LicenseGateLemonSqueezyTest {

    private static final long OUR_STORE = 42L;

    private HttpServer server;
    private URI baseUri;
    private volatile int responseStatus;
    private volatile String responseBody;

    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            lastPath.set(ex.getRequestURI().getPath());
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private LicenseGate newGate() {
        return LicenseGate.of(LicenseConfig.builder()
            .licenseProvider(LicenseConfig.Provider.LEMONSQUEEZY)
            .lemonSqueezyStoreId(OUR_STORE)
            .lemonSqueezyBaseUri(baseUri)
            .lemonSqueezyTimeout(Duration.ofSeconds(3))
            .build());
    }

    private static String validPayload(String email) {
        return "{\"valid\":true,\"error\":null,"
            + "\"license_key\":{\"status\":\"inactive\"},"
            + "\"meta\":{\"store_id\":" + OUR_STORE + ",\"customer_email\":\"" + email + "\"}}";
    }

    @Test
    void lemonSqueezyProviderNeedsNoKeygenCredentials() {
        // The whole point of the provider split: a LemonSqueezy-only consumer should not have to
        // invent a Keygen account to construct a config.
        assertDoesNotThrow(this::newGate);
    }

    @Test
    void commercialEmailWithValidKeyIsAllowedViaLemonSqueezy() {
        responseStatus = 200;
        responseBody = validPayload("bob@acme-corp.com");

        LicenseResult r = newGate().check("bob@acme-corp.com", "KEY-OK");

        assertEquals(LicenseResult.AllowedReason.LICENSE_VALID,
            ((LicenseResult.Allowed) r).reason());
        assertEquals("/v1/licenses/validate", lastPath.get());
        assertEquals("license_key=KEY-OK", lastBody.get());
    }

    @Test
    void freeProviderEmailIsLetThroughWithoutHittingLemonSqueezy() {
        responseStatus = 500;                  // would fail if we called the API
        responseBody = "boom";
        LicenseResult r = newGate().check("alice@gmail.com", null);
        assertEquals(LicenseResult.AllowedReason.FREE_PROVIDER_EMAIL,
            ((LicenseResult.Allowed) r).reason());
        assertNull(lastPath.get(), "no request should have been made");
    }

    @Test
    void commercialEmailWithoutKeyIsDenied() {
        LicenseResult r = newGate().check("bob@acme-corp.com", null);
        assertEquals(LicenseResult.DeniedReason.LICENSE_REQUIRED,
            ((LicenseResult.Denied) r).reason());
    }

    @Test
    void networkErrorFailsClosedByDefault() {
        responseStatus = 503;
        responseBody = "{}";
        LicenseResult r = newGate().check("bob@acme-corp.com", "KEY");
        assertEquals(LicenseResult.DeniedReason.NETWORK_ERROR,
            ((LicenseResult.Denied) r).reason());
    }

    @Test
    void networkErrorFailsOpenWhenOptedIn() {
        responseStatus = 503;
        responseBody = "{}";
        LicenseGate gate = LicenseGate.of(LicenseConfig.builder()
            .licenseProvider(LicenseConfig.Provider.LEMONSQUEEZY)
            .lemonSqueezyStoreId(OUR_STORE)
            .lemonSqueezyBaseUri(baseUri)
            .lemonSqueezyTimeout(Duration.ofSeconds(3))
            .allowOnNetworkError(true)
            .build());

        LicenseResult r = gate.check("bob@acme-corp.com", "KEY");

        assertEquals(LicenseResult.AllowedReason.NETWORK_ERROR_ALLOWED,
            ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void storeIdIsRequiredForTheLemonSqueezyProvider() {
        LicenseException e = assertThrows(LicenseException.class, () -> LicenseConfig.builder()
            .licenseProvider(LicenseConfig.Provider.LEMONSQUEEZY)
            .build());
        assertTrue(e.getMessage().contains("lemonSqueezyStoreId"), e.getMessage());
    }

    @Test
    void mockModeSkipsTheStoreIdRequirementAndTheNetwork() {
        responseStatus = 500;
        responseBody = "boom";
        LicenseGate gate = LicenseGate.of(LicenseConfig.builder()
            .licenseProvider(LicenseConfig.Provider.LEMONSQUEEZY)
            .mockMode(true)
            .build());

        LicenseResult r = gate.check("bob@acme-corp.com", "ANY");

        assertEquals(LicenseResult.AllowedReason.MOCKED_ALLOWED,
            ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void defaultProviderIsStillKeygen() {
        // Regression guard: every consumer built before 0.4.0 must keep validating against Keygen
        // without naming a provider.
        LicenseConfig cfg = LicenseConfig.builder()
            .keygenAccountId("a").keygenApiKey("k").build();
        assertEquals(LicenseConfig.Provider.KEYGEN, cfg.licenseProvider());
    }

    @Test
    void keygenCredentialsAreStillRequiredForTheDefaultProvider() {
        assertThrows(LicenseException.class, () -> LicenseConfig.builder().build());
    }
}
