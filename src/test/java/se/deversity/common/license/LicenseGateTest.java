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
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class LicenseGateTest {

    private HttpServer server;
    private URI baseUri;
    private volatile int responseStatus;
    private volatile String responseBody;

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
            byte[] body = responseBody == null ? new byte[0] : responseBody.getBytes();
            ex.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private LicenseGate newGate(boolean allowOnNetworkError) {
        return LicenseGate.of(LicenseConfig.builder()
            .keygenAccountId("acct_x")
            .keygenApiKey("api_key_x")
            .keygenBaseUri(baseUri)
            .keygenTimeout(Duration.ofSeconds(3))
            .lemonSqueezyStoreSubdomain("my-store")
            .allowOnNetworkError(allowOnNetworkError)
            .build());
    }

    @Test
    void freeProviderEmailIsLetThroughWithoutHittingKeygen() {
        responseStatus = 500;                  // would fail if we called Keygen
        responseBody = "boom";
        LicenseResult r = newGate(false).check("alice@gmail.com", null);
        assertEquals(LicenseResult.AllowedReason.FREE_PROVIDER_EMAIL,
            ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void commercialEmailWithoutKeyIsDenied() {
        LicenseResult r = newGate(false).check("bob@acme-corp.com", null);
        assertEquals(LicenseResult.DeniedReason.LICENSE_REQUIRED,
            ((LicenseResult.Denied) r).reason());
    }

    @Test
    void invalidEmailIsDenied() {
        LicenseResult r = newGate(false).check("not-an-email", null);
        assertEquals(LicenseResult.DeniedReason.INVALID_EMAIL,
            ((LicenseResult.Denied) r).reason());
    }

    @Test
    void commercialEmailWithValidKeyIsAllowed() {
        responseStatus = 200;
        responseBody = "{\"meta\":{\"valid\":true,\"code\":\"VALID\"}}";
        LicenseResult r = newGate(false).check("bob@acme-corp.com", "KEY-OK");
        assertEquals(LicenseResult.AllowedReason.LICENSE_VALID,
            ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void networkErrorFailsClosedByDefault() {
        responseStatus = 503;
        responseBody = "{}";
        LicenseResult r = newGate(false).check("bob@acme-corp.com", "KEY");
        assertEquals(LicenseResult.DeniedReason.NETWORK_ERROR,
            ((LicenseResult.Denied) r).reason());
    }

    @Test
    void networkErrorFailsOpenWhenOptedIn() {
        responseStatus = 503;
        responseBody = "{}";
        LicenseResult r = newGate(true).check("bob@acme-corp.com", "KEY");
        assertEquals(LicenseResult.AllowedReason.NETWORK_ERROR_ALLOWED,
            ((LicenseResult.Allowed) r).reason());
    }

    @Test
    void checkoutUrlRoutesThroughLemonSqueezy() {
        URI url = newGate(false).checkoutUrl("bob@acme-corp.com", "VAR");
        assertTrue(url.toString().startsWith("https://my-store.lemonsqueezy.com/buy/VAR"),
            "got: " + url);
    }

    @Test
    void checkoutUrlThrowsWhenStoreSubdomainIsMissing() {
        LicenseGate gate = LicenseGate.of(LicenseConfig.builder()
            .keygenAccountId("a").keygenApiKey("k").build());
        assertThrows(LicenseException.class, () -> gate.checkoutUrl("e@x.com", "VAR"));
    }
}
