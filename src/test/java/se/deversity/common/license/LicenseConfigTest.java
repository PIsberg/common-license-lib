package se.deversity.common.license;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LicenseConfigTest {

    @Test
    void builderRequiresKeygenCredentials() {
        assertThrows(LicenseException.class,
            () -> LicenseConfig.builder().keygenApiKey("k").build());
        assertThrows(LicenseException.class,
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
}
