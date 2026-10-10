package se.deversity.common.license;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class LicenseGateTest {

    private HttpServer server;
    private URI baseUri;
    private volatile int responseStatus;
    private volatile String responseBody;

    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();

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
            lastRequestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
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
    void injectedHttpClientCarriesTheValidationRequest() {
        // keygen.invalid never resolves, so the only way to reach the loopback server is through
        // the injected client's proxy. A gate that built its own client would get NETWORK_ERROR.
        responseStatus = 200;
        responseBody = "{\"meta\":{\"valid\":true,\"code\":\"VALID\"}}";
        List<URI> proxied = new CopyOnWriteArrayList<>();
        Proxy loopback = new Proxy(Proxy.Type.HTTP, server.getAddress());
        HttpClient injected = HttpClient.newBuilder()
            .proxy(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {
                    proxied.add(uri);
                    return List.of(loopback);
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                }
            })
            .build();

        LicenseGate gate = LicenseGate.of(LicenseConfig.builder()
            .keygenAccountId("acct_x")
            .keygenApiKey("api_key_x")
            .keygenBaseUri(URI.create("http://keygen.invalid"))
            .keygenTimeout(Duration.ofSeconds(3))
            .httpClient(injected)
            .build());

        LicenseResult r = gate.check("bob@acme-corp.com", "KEY-OK");

        assertEquals(LicenseResult.AllowedReason.LICENSE_VALID,
            assertInstanceOf(LicenseResult.Allowed.class, r).reason());
        assertEquals(1, proxied.size(), "validation request should go through the injected client");
        assertEquals("keygen.invalid", proxied.get(0).getHost());
    }

    @Test
    void configuredProductIdReachesTheKeygenRequest() {
        responseStatus = 200;
        responseBody = "{\"meta\":{\"valid\":true,\"code\":\"VALID\"}}";

        LicenseGate gate = LicenseGate.of(LicenseConfig.builder()
            .keygenAccountId("acct_x")
            .keygenApiKey("api_key_x")
            .keygenProductId("prod_x")
            .keygenBaseUri(baseUri)
            .keygenTimeout(Duration.ofSeconds(3))
            .build());

        gate.check("bob@acme-corp.com", "KEY-OK");

        assertTrue(lastRequestBody.get().contains("\"product\":\"prod_x\""), lastRequestBody.get());
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
