package se.deversity.common.license.lemonsqueezy;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import se.deversity.vibetags.annotations.AIAudit;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AIPublicAPI;
import se.deversity.vibetags.annotations.AISecure;

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
@AISecure(aspect = "webhook signature verification")
@AIAudit(checkFor = {"Timing attack", "Authentication Bypass"})
@AIPublicAPI(reason = "Called directly from consumer webhook handlers; a signature change breaks every caller.")
@AIContext(
    focus = "Constant-time comparison. Digest equality goes through MessageDigest.isEqual and nothing else.",
    avoids = "String#equals, Arrays#equals, Objects#equals on digests; early-return on first mismatching byte"
)
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

    /**
     * Hex decoder, case-insensitive, ASCII digits only. Returns {@code null} on any parse error
     * (bad length, bad char). {@link Character#digit(char, int)} is deliberately not used: it
     * also accepts fullwidth and other Unicode digits, which gave one signature many spellings.
     */
    static byte[] decodeHex(String hex) {
        String s = hex.trim();
        if ((s.length() & 1) == 1) {
            return null;
        }
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = hexDigit(s.charAt(i * 2));
            int lo = hexDigit(s.charAt(i * 2 + 1));
            if (hi < 0 || lo < 0) {
                return null;
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static int hexDigit(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }
}
