package se.deversity.common.license.keygen;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.deversity.common.license.LicenseException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Drives the issuer against a real loopback HTTP server ({@link HttpServer}) so the JSON:API
 * request/response wire format is exercised end-to-end without any external dep.
 */
class KeygenIssuerTest {

    private static final String TOKEN = "admin-token-do-not-leak";

    private record Recorded(String method, String path, String auth, String body) {
    }

    private record Scripted(int status, String body) {
    }

    private HttpServer server;
    private KeygenIssuer issuer;

    private final List<Recorded> requests = new ArrayList<>();
    private final List<Scripted> responses = new ArrayList<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        issuer = new KeygenIssuer(HttpClient.newHttpClient(), "acct-1", TOKEN,
            baseUri, Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        try (ex) {
            requests.add(new Recorded(
                ex.getRequestMethod(),
                ex.getRequestURI().toString(),
                ex.getRequestHeaders().getFirst("Authorization"),
                new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Scripted scripted = responses.remove(0);
            byte[] body = scripted.body().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/vnd.api+json");
            ex.sendResponseHeaders(scripted.status(), body.length);
            ex.getResponseBody().write(body);
        }
    }

    @Test
    void createsUserAndReturnsItsId() {
        responses.add(new Scripted(201, "{\"data\":{\"id\":\"user-42\",\"type\":\"users\"}}"));

        String id = issuer.ensureUser("ada@corp.com");

        assertEquals("user-42", id);
        Recorded r = requests.get(0);
        assertEquals("POST", r.method());
        assertEquals("/v1/accounts/acct-1/users", r.path());
        assertEquals("Bearer " + TOKEN, r.auth());
        assertEquals("{\"data\":{\"type\":\"users\",\"attributes\":{\"email\":\"ada@corp.com\"}}}",
            r.body());
    }

    @Test
    void fallsBackToLookupWhenUserAlreadyExists() {
        responses.add(new Scripted(422,
            "{\"errors\":[{\"title\":\"Unprocessable resource\"}]}"));
        responses.add(new Scripted(200, "{\"data\":{\"id\":\"user-7\",\"type\":\"users\"}}"));

        String id = issuer.ensureUser("ada@corp.com");

        assertEquals("user-7", id);
        Recorded lookup = requests.get(1);
        assertEquals("GET", lookup.method());
        assertEquals("/v1/accounts/acct-1/users/ada%40corp.com", lookup.path());
    }

    @Test
    void createsLicenseAndReturnsTheKey() {
        responses.add(new Scripted(201,
            "{\"data\":{\"id\":\"lic-1\",\"type\":\"licenses\","
                + "\"attributes\":{\"key\":\"AAAA-BBBB-CCCC-V3\"}}}"));

        String key = issuer.createLicense("policy-1", "user-42");

        assertEquals("AAAA-BBBB-CCCC-V3", key);
        Recorded r = requests.get(0);
        assertEquals("POST", r.method());
        assertEquals("/v1/accounts/acct-1/licenses", r.path());
        assertEquals("{\"data\":{\"type\":\"licenses\",\"relationships\":{"
                + "\"policy\":{\"data\":{\"type\":\"policies\",\"id\":\"policy-1\"}},"
                + "\"owner\":{\"data\":{\"type\":\"users\",\"id\":\"user-42\"}}"
                + "}}}",
            r.body());
    }

    @Test
    void issueLicenseChainsUserAndLicenseCreation() {
        responses.add(new Scripted(201, "{\"data\":{\"id\":\"user-9\",\"type\":\"users\"}}"));
        responses.add(new Scripted(201,
            "{\"data\":{\"id\":\"lic-2\",\"type\":\"licenses\","
                + "\"attributes\":{\"key\":\"KEY-2\"}}}"));

        assertEquals("KEY-2", issuer.issueLicense("policy-1", "ada@corp.com"));

        assertEquals(2, requests.size());
        assertTrue(requests.get(1).body().contains("\"id\":\"user-9\""));
    }

    @Test
    void surfacesKeygenErrorTitleWithoutEchoingTheToken() {
        responses.add(new Scripted(403, "{\"errors\":[{\"title\":\"Access denied\"}]}"));

        LicenseException e = assertThrows(LicenseException.class,
            () -> issuer.ensureUser("ada@corp.com"));

        assertTrue(e.getMessage().contains("HTTP 403"), e.getMessage());
        assertTrue(e.getMessage().contains("Access denied"), e.getMessage());
        assertFalse(e.getMessage().contains(TOKEN), "token leaked: " + e.getMessage());
    }

    @Test
    void failsLoudlyWhenLicenseResponseCarriesNoKey() {
        responses.add(new Scripted(201, "{\"data\":{\"id\":\"lic-3\",\"type\":\"licenses\"}}"));

        assertThrows(LicenseException.class,
            () -> issuer.createLicense("policy-1", "user-42"));
    }

    @Test
    void escapesHostileEmailInJsonAndPath() {
        responses.add(new Scripted(422, "{\"errors\":[{\"title\":\"dup\"}]}"));
        responses.add(new Scripted(200, "{\"data\":{\"id\":\"user-8\",\"type\":\"users\"}}"));

        issuer.ensureUser("a\"b@corp.com");

        assertEquals("{\"data\":{\"type\":\"users\",\"attributes\":{\"email\":\"a\\\"b@corp.com\"}}}",
            requests.get(0).body());
        assertEquals("/v1/accounts/acct-1/users/a%22b%40corp.com", requests.get(1).path());
    }
}
