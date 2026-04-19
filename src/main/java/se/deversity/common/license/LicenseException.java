package se.deversity.common.license;

/**
 * Thrown for programmer errors in configuring or invoking the library
 * (e.g. missing required credentials, malformed override lists).
 *
 * <p>Operational outcomes — invalid/expired/unknown licenses, network timeouts —
 * are returned via {@link LicenseResult.Denied}, not thrown. This exception
 * only surfaces mis-use of the API.
 */
public class LicenseException extends RuntimeException {

    public LicenseException(String message) {
        super(message);
    }

    public LicenseException(String message, Throwable cause) {
        super(message, cause);
    }
}
