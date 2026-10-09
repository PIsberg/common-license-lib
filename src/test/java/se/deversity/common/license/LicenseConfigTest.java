package se.deversity.common.license;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LicenseConfigTest {

    @Test
    void builderRequiresKeygenAccountIdButNotApiKey() {
        // The account id scopes the request and cannot be guessed, so it stays required.
        assertThrows(LicenseException.class,
            () -> LicenseConfig.builder().keygenApiKey("k").build());
        // The API key must NOT be required: validate-key is public, end users never hold a
        // token, and forcing a placeholder makes Keygen 401 before it evaluates the license.
        assertDoesNotThrow(
            () -> LicenseConfig.builder().keygenAccountId("a").build());
    }

    @Test
    void toStringRedactsSecrets() {
        LicenseConfig cfg = LicenseConfig.builder()
            .keygenAccountId("acct_x")
            .keygenApiKey("super-secret-key")
            .lemonSqueezySigningSecret("whsec_123")
            .build();
        String s = cfg.toString();
        assertFalse(s.contains("super-secret-key"), s);
        assertFalse(s.contains("whsec_123"), s);
        assertTrue(s.contains("***"), s);
    }

    @Test
    void defaultsAreSensible() {
        LicenseConfig cfg = LicenseConfig.builder()
            .keygenAccountId("a").keygenApiKey("k").build();
        assertEquals("https://api.keygen.sh", cfg.keygenBaseUri().toString());
        assertFalse(cfg.allowOnNetworkError());
        assertNotNull(cfg.emailClassifier());
    }

    @Test
    void mockModePlaceholdersDoNotLeakIntoALaterRealBuild() {
        // A builder reused across environments (mock in tests, real in prod) must not carry the
        // mock placeholders forward: a "mocked" account id would satisfy the required-field check
        // and send every real validation to a non-existent Keygen account.
        LicenseConfig.Builder b = LicenseConfig.builder();
        LicenseConfig mocked = b.mockMode(true).build();
        assertEquals("mocked", mocked.keygenAccountId());

        assertThrows(LicenseException.class, () -> b.mockMode(false).build());
    }

    @Test
    void mockModeDoesNotInventABearerTokenForALaterRealBuild() {
        LicenseConfig.Builder b = LicenseConfig.builder().keygenAccountId("acct");
        b.mockMode(true).build();

        // Keygen 401s a bogus bearer before evaluating the license, denying every customer.
        assertNull(b.mockMode(false).build().keygenApiKey());
    }

    @Test
    void toStringShowsMockMode() {
        // mockMode lets everyone through; a diagnostic dump that hides it hides the one flag
        // that must never reach production.
        String s = LicenseConfig.builder().mockMode(true).build().toString();
        assertTrue(s.contains("mockMode=true"), s);
    }

    @Test
    void nonPositiveTimeoutsAreRejectedAtConfigurationTime() {
        // HttpRequest.Builder#timeout throws on a non-positive duration, so accepting one here
        // turned every check() into an IllegalArgumentException instead of a result.
        LicenseConfig.Builder b = LicenseConfig.builder();
        assertThrows(IllegalArgumentException.class, () -> b.keygenTimeout(java.time.Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> b.keygenTimeout(java.time.Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> b.lemonSqueezyTimeout(java.time.Duration.ZERO));
    }

    @Test
    void keygenApiKeyIsOptionalBecauseValidateKeyIsPublic() {
        // End users are never issued a Keygen token. Requiring one forced callers to invent a
        // placeholder, and Keygen rejects a bogus bearer with 401 before evaluating the license,
        // which denied every legitimate customer run.
        LicenseConfig cfg = LicenseConfig.builder()
            .licenseProvider(LicenseConfig.Provider.KEYGEN)
            .keygenAccountId("acct")
            .build();

        assertNull(cfg.keygenApiKey());
    }
}
