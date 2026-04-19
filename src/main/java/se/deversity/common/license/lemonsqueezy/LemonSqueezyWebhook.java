package se.deversity.common.license.lemonsqueezy;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utilities for verifying inbound webhook requests from LemonSqueezy.
 *
 * <p>LemonSqueezy signs every webhook with HMAC-SHA256 over the raw request body
 * using your store's signing secret, and sends the hex digest in the
 * {@code X-Signature} header. See
 * <a href="https://docs.lemonsqueezy.com/help/webhooks#signing-requests">Signing requests</a>.
 *
 * <p>All comparisons are constant-time via {@link MessageDigest#isEqual(byte[], byte[])}.
 */
public final class LemonSqueezyWebhook {

    private static final String ALG = "HmacSHA256";

    private LemonSqueezyWebhook() {
    }

    /**
     * Return {@code true} iff {@code receivedSignatureHex} is a valid HMAC-SHA256 of
     * {@code rawBody} under {@code signingSecret}.
     *
     * @param rawBody              raw request bytes — must not be re-serialized from parsed JSON
     * @param receivedSignatureHex value of the {@code X-Signature} header
     * @param signingSecret        store webhook signing secret (from the LS dashboard)
     */
    public static boolean verifySignature(byte[] rawBody, String receivedSignatureHex, String signingSecret) {
        if (rawBody == null || receivedSignatureHex == null || signingSecret == null) {
            return false;
        }
        byte[] expected = hmacSha256(rawBody, signingSecret.getBytes(StandardCharsets.UTF_8));
        byte[] received = decodeHex(receivedSignatureHex);
        if (received == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, received);
    }

    static byte[] hmacSha256(byte[] message, byte[] key) {
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(new SecretKeySpec(key, ALG));
            return mac.doFinal(message);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** Lenient hex decoder. Returns {@code null} on any parse error (bad length, bad char). */
    static byte[] decodeHex(String hex) {
        String s = hex.trim();
        if ((s.length() & 1) == 1) {
            return null;
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }
}
