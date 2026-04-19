package se.deversity.common.license.email;

/**
 * Classification buckets for an email address when deciding whether a user
 * is subject to the commercial-license gate.
 */
public enum EmailClassification {

    /** Address is hosted on a well-known consumer / free-mail provider. Let through without a key. */
    FREE_PROVIDER,

    /** Address is on a domain that the library treats as commercial. Requires a valid license key. */
    COMMERCIAL,

    /** Address could not be parsed as a valid email (missing @, empty, etc). */
    INVALID
}
