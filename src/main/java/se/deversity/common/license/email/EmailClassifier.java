package se.deversity.common.license.email;

/**
 * Decides whether an email address counts as a {@link EmailClassification#FREE_PROVIDER}
 * (let through) or {@link EmailClassification#COMMERCIAL} (must present a license key).
 *
 * <p>Implementations must be thread-safe and side-effect free — they may be called on
 * the hot path of {@code LicenseGate.check(...)}.
 */
@FunctionalInterface
public interface EmailClassifier {

    /**
     * Classify {@code email}. Implementations should not throw on malformed input;
     * return {@link EmailClassification#INVALID} instead.
     */
    EmailClassification classify(String email);
}
