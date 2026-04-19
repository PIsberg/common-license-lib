package se.deversity.common.license.email;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AllowListEmailClassifierTest {

    private final AllowListEmailClassifier defaultClassifier =
        new AllowListEmailClassifier(null, null);

    @ParameterizedTest(name = "[{index}] {0} -> {1}")
    @CsvSource({
        "alice@gmail.com,           FREE_PROVIDER",
        "Alice@Gmail.COM,           FREE_PROVIDER",
        "bob+tag@outlook.com,       FREE_PROVIDER",
        "carol@icloud.com,          FREE_PROVIDER",
        "dave@protonmail.com,       FREE_PROVIDER",
        "erin@proton.me,            FREE_PROVIDER",
        "frank@yahoo.co.uk,         FREE_PROVIDER",
        "grace@acme-corp.com,       COMMERCIAL",
        "heidi@sub.gmail.com,       COMMERCIAL",
        "ivan@,                     INVALID",
        "@gmail.com,                INVALID",
        "'',                        INVALID",
        "no-at-sign,                INVALID"
    })
    void classifyHandlesTheExpectedCases(String email, EmailClassification expected) {
        assertEquals(expected, defaultClassifier.classify(email));
    }

    @org.junit.jupiter.api.Test
    void additionalFreeProvidersAreHonored() {
        AllowListEmailClassifier c = new AllowListEmailClassifier(
            Set.of("my-intranet.example"), null);
        assertEquals(EmailClassification.FREE_PROVIDER, c.classify("bob@my-intranet.example"));
        assertEquals(EmailClassification.FREE_PROVIDER, c.classify("alice@gmail.com"));
    }

    @org.junit.jupiter.api.Test
    void additionalCommercialProvidersBeatBundledList() {
        AllowListEmailClassifier c = new AllowListEmailClassifier(
            null, Set.of("gmail.com"));
        assertEquals(EmailClassification.COMMERCIAL, c.classify("alice@gmail.com"));
        assertEquals(EmailClassification.FREE_PROVIDER, c.classify("bob@outlook.com"));
    }

    @org.junit.jupiter.api.Test
    void nullAndEmptyInputClassifyAsInvalid() {
        assertEquals(EmailClassification.INVALID, defaultClassifier.classify(null));
        assertEquals(EmailClassification.INVALID, defaultClassifier.classify("   "));
    }

    @org.junit.jupiter.api.Test
    void bundledSetIsNonTrivial() {
        assertTrue(FreeProviders.bundled().size() > 20,
            "Expected at least 20 bundled providers, got " + FreeProviders.bundled().size());
        assertTrue(FreeProviders.bundled().contains("gmail.com"));
    }
}
