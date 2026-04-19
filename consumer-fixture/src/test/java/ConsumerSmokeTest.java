import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.deversity.common.license.LicenseConfig;
import se.deversity.common.license.LicenseGate;
import se.deversity.common.license.LicenseResult;
import se.deversity.common.license.lemonsqueezy.LemonSqueezyWebhook;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the public API exactly as a downstream consumer would, against the
 * license-lib artifact installed into .m2. This catches classloader / visibility
 * problems that unit tests inside the library module can't see.
 */
class ConsumerSmokeTest {

    private HttpServer server;
    private URI baseUri;
    private volatile int status = 200;
    private volatile String body = "{\"meta\":{\"valid\":true,\"code\":\"VALID\"}}";

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
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(b);
            }
        }
    }

    private LicenseGate gate() {
        return LicenseGate.of(LicenseConfig.builder()
            .keygenAccountId("acct")
            .keygenApiKey("key")
            .keygenBaseUri(baseUri)
            .keygenTimeout(Duration.ofSeconds(3))
            .lemonSqueezyStoreSubdomain("my-store")
            .build());
    }

    @Test
    void freeProviderPath() {
        LicenseResult r = gate().check("alice@gmail.com", null);
        assertInstanceOf(LicenseResult.Allowed.class, r);
    }

    @Test
    void commercialWithoutKeyPath() {
        LicenseResult r = gate().check("bob@acme-corp.com", null);
        assertEquals(LicenseResult.DeniedReason.LICENSE_REQUIRED,
            ((LicenseResult.Denied) r).reason());
    }

    @Test
    void commercialWithValidKeyPath() {
        LicenseResult r = gate().check("bob@acme-corp.com", "KEY-OK");
        assertInstanceOf(LicenseResult.Allowed.class, r);
    }

    @Test
    void checkoutUrlBuilds() {
        URI url = gate().checkoutUrl("bob@acme-corp.com", "VAR123");
        assertTrue(url.toString().contains("my-store.lemonsqueezy.com/buy/VAR123"),
            "got: " + url);
    }

    @Test
    void webhookSignatureVerifies() {
        // Same reference vector as LemonSqueezyWebhookTest, consumed via the public static method.
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        String sig = "88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b";
        assertTrue(LemonSqueezyWebhook.verifySignature(body, sig, "secret"));
    }
}
