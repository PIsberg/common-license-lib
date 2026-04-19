package se.deversity.common.license;

/**
 * Outcome of {@code LicenseGate.check(email, licenseKey)}.
 *
 * <p>Sealed: exactly two shapes — {@link Allowed} (let the user through) and
 * {@link Denied} (block or upsell). Consumers are expected to use an exhaustive
 * {@code switch} pattern.
 */
public sealed interface LicenseResult {

    /**
     * User may proceed.
     *
     * @param reason why the gate let them through (free email, valid license, …)
     */
    record Allowed(AllowedReason reason) implements LicenseResult {
        public Allowed {
            if (reason == null) {
                throw new IllegalArgumentException("reason must not be null");
            }
        }
    }

    /**
     * User must be blocked or shown a paywall.
     *
     * @param reason  machine-readable reason code
     * @param message optional human-readable context (may be {@code null})
     */
    record Denied(DeniedReason reason, String message) implements LicenseResult {
        public Denied {
            if (reason == null) {
                throw new IllegalArgumentException("reason must not be null");
            }
        }

        public static Denied of(DeniedReason reason) {
            return new Denied(reason, null);
        }
    }

    /** Structured reason an {@link Allowed} was issued. */
    enum AllowedReason {
        /** Address is on the curated free-provider allow-list. */
        FREE_PROVIDER_EMAIL,
        /** A license key was supplied and Keygen said it's valid. */
        LICENSE_VALID,
        /** Network failed but the consumer opted in to fail-open. */
        NETWORK_ERROR_ALLOWED,
        /** Mock mode enabled - always allowed for testing. */
        MOCKED_ALLOWED
    }

    /** Structured reason a {@link Denied} was issued. */
    enum DeniedReason {
        /** Email could not be parsed. */
        INVALID_EMAIL,
        /** Commercial email but no license key supplied. */
        LICENSE_REQUIRED,
        /** Keygen said the key doesn't exist. */
        LICENSE_NOT_FOUND,
        /** Keygen said the key is expired. */
        LICENSE_EXPIRED,
        /** Keygen said the key is suspended/revoked. */
        LICENSE_SUSPENDED,
        /** Keygen said the key is invalid for some other reason (e.g. wrong scope). */
        LICENSE_INVALID,
        /** HTTP call to Keygen failed and fail-open is not enabled. */
        NETWORK_ERROR
    }
}
