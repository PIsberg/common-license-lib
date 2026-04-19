package se.deversity.common.license.keygen;

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
 * Drives the validator against a real loopback HTTP server ({@link HttpServer}) so the
 * JSON:API request/response wire format is exercised end-to-end without any external dep.
 */
class KeygenValidatorTest {

    private HttpServer server;
    private URI baseUri;
    private HttpClient http;

    private final AtomicReference<String> lastBody = new AtomicReference<>();
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
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            byte[] req = ex.getRequestBody().readAllBytes();
            lastBody.set(new String(req, StandardCharsets.UTF_8));

            byte[] body = responseBody == null ? new byte[0] : responseBody.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/vnd.api+json");
            ex.sendResponseHeaders(responseStatus, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        }
    }

    private KeygenValidator newValidator() {
        return new KeygenValidator(http, "acct_x", "api_key_x", baseUri, Duration.ofSeconds(3));
    }

    @Test
    void sendsBearerAuthAndScopedBody() {
        responseStatus = 200;
        responseBody = "{\"meta\":{\"valid\":true,\"code\":\"VALID\"}}";

        LicenseResult r = newValidator().validate("KEY-123", "ada@corp.com");

        assertInstanceOf(LicenseResult.Allowed.class, r);
        assertEquals(AllowedReason.LICENSE_VALID, ((LicenseResult.Allowed) r).reason());
        assertEquals("Bearer api_key_x", lastAuth.get());
        assertTrue(lastBody.get().contains("\"key\":\"KEY-123\""), lastBody.get());
        assertTrue(lastBody.get().contains("\"email\":\"ada@corp.com\""), lastBody.get());
    }

    @Test
    void mapsExpiredCode() {
        responseStatus = 200;
        responseBody = "{\"meta\":{\"valid\":false,\"code\":\"EXPIRED\"}}";
        LicenseResult r = newValidator().validate("KEY", "x@corp.com");
        assertEquals(DeniedReason.LICENSE_EXPIRED, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsSuspendedCode() {
        responseStatus = 200;
        responseBody = "{\"meta\":{\"valid\":false,\"code\":\"SUSPENDED\"}}";
        LicenseResult r = newValidator().validate("KEY", "x@corp.com");
        assertEquals(DeniedReason.LICENSE_SUSPENDED, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsNotFoundCodeFromPayload() {
        responseStatus = 200;
        responseBody = "{\"meta\":{\"valid\":false,\"code\":\"NOT_FOUND\"}}";
        LicenseResult r = newValidator().validate("KEY", "x@corp.com");
        assertEquals(DeniedReason.LICENSE_NOT_FOUND, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void maps404StatusToLicenseNotFound() {
        responseStatus = 404;
        responseBody = "{}";
        LicenseResult r = newValidator().validate("KEY", "x@corp.com");
        assertEquals(DeniedReason.LICENSE_NOT_FOUND, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsUnauthorizedToNetworkErrorToAvoidSilentBypass() {
        responseStatus = 401;
        responseBody = "{}";
        LicenseResult r = newValidator().validate("KEY", "x@corp.com");
        assertEquals(DeniedReason.NETWORK_ERROR, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsServerErrorToNetworkError() {
        responseStatus = 503;
        responseBody = "{}";
        LicenseResult r = newValidator().validate("KEY", "x@corp.com");
        assertEquals(DeniedReason.NETWORK_ERROR, ((LicenseResult.Denied) r).reason());
    }

    @Test
    void mapsMalformedJsonToNetworkError() {
        responseStatus = 200;
        responseBody = "not-json";
        LicenseResult r = newValidator().validate("KEY", "x@corp.com");
        assertEquals(DeniedReason.NETWORK_ERROR, ((LicenseResult.Denied) r).reason());
    }
}
