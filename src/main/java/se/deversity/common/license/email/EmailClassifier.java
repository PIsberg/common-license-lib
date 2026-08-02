package se.deversity.common.license.email;

import se.deversity.vibetags.annotations.AIContract;
import se.deversity.vibetags.annotations.AIPublicAPI;

/**
 * Decides whether an email address counts as a {@link EmailClassification#FREE_PROVIDER}
 * (let through) or {@link EmailClassification#COMMERCIAL} (must present a license key).
 *
 * <p>Implementations must be thread-safe and side-effect free — they may be called on
 * the hot path of {@code LicenseGate.check(...)}.
 */
@FunctionalInterface
@AIPublicAPI(reason = "Consumers replace the whole classification strategy through this interface.")
public interface EmailClassifier {

    /**
     * Classify {@code email}. Implementations should not throw on malformed input;
     * return {@link EmailClassification#INVALID} instead.
     */
    @AIContract(reason = "The single abstract method of a @FunctionalInterface. Consumers implement it "
        + "with a lambda via LicenseConfig.Builder#emailClassifier, so any change to the name, parameter "
        + "list or return type breaks every custom classifier at compile time.")
    EmailClassification classify(String email);
}
