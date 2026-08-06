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
