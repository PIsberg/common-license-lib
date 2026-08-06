package se.deversity.common.license.paddle;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import se.deversity.vibetags.annotations.AIAudit;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AIPublicAPI;
import se.deversity.vibetags.annotations.AISecure;

/**
 * Utilities for verifying inbound webhook requests from Paddle Billing.
 *
 * <p>Paddle signs every webhook with HMAC-SHA256 over {@code ts + ":" + rawBody} using the
 * notification destination's endpoint secret key, and sends
 * {@code Paddle-Signature: ts=<unix>;h1=<hex>} — {@code h1} may appear more than once while a
 * secret is being rotated, and a signature is valid if it matches <em>any</em> {@code h1}. See
 * <a href="https://developer.paddle.com/webhooks/signature-verification">Verify webhook
 * signatures</a>.
 *
 * <p>All comparisons are constant-time via {@link MessageDigest#isEqual(byte[], byte[])}.
 */
@AISecure(aspect = "webhook signature verification")
@AIAudit(checkFor = {"Timing attack", "Authentication Bypass", "Replay attack"})
@AIPublicAPI(reason = "Called directly from consumer webhook handlers; a signature change breaks every caller.")
@AIContext(
    focus = "Constant-time comparison against every h1 candidate — evaluate all candidates, never "
        + "early-return between them on partial match. Replay protection is opt-in via the "
        + "maxAgeSeconds overload; the two-argument form documents that it does not check ts.",
    avoids = "String#equals, Arrays#equals, Objects#equals on digests; parsing the body before "
        + "verification; any JSON dependency"
)
public final class PaddleWebhook {

    private static final String ALG = "HmacSHA256";

    private PaddleWebhook() {
    }

    /**
     * Return {@code true} iff {@code paddleSignatureHeader} contains an {@code h1} that is a
     * valid HMAC-SHA256 of {@code ts + ":" + rawBody} under {@code endpointSecret}.
     *
     * <p>This overload does <strong>not</strong> check the age of {@code ts}; use
     * {@link #verifySignature(byte[], String, String, long, Clock)} to also reject replayed
     * events.
     *
     * @param rawBody               raw request bytes — must not be re-serialized from parsed JSON
     * @param paddleSignatureHeader value of the {@code Paddle-Signature} header
     * @param endpointSecret        notification destination secret ({@code pdl_ntfset_...})
     */
    public static boolean verifySignature(byte[] rawBody, String paddleSignatureHeader,
                                          String endpointSecret) {
        return verify(rawBody, paddleSignatureHeader, endpointSecret, -1, null);
    }

    /**
     * As {@link #verifySignature(byte[], String, String)}, additionally rejecting events whose
     * {@code ts} is more than {@code maxAgeSeconds} behind {@code clock} (or any amount ahead
     * of it), which closes the replay window.
     *
     * @param maxAgeSeconds maximum accepted age of the event; must be {@code >= 0}
     * @param clock         source of "now"; pass {@link Clock#systemUTC()} in production
     */
    public static boolean verifySignature(byte[] rawBody, String paddleSignatureHeader,
                                          String endpointSecret, long maxAgeSeconds, Clock clock) {
        if (maxAgeSeconds < 0 || clock == null) {
            return false;
        }
        return verify(rawBody, paddleSignatureHeader, endpointSecret, maxAgeSeconds, clock);
    }

    private static boolean verify(byte[] rawBody, String header, String secret,
                                  long maxAgeSeconds, Clock clock) {
        if (rawBody == null || header == null || secret == null) {
            return false;
        }
        String ts = null;
        // At most a handful of ';'-separated k=v pairs; h1 may repeat during secret rotation.
        String[] parts = header.split(";");
        int candidates = 0;
        byte[][] received = new byte[parts.length][];
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = part.substring(0, eq).trim();
            String value = part.substring(eq + 1).trim();
            if (key.equals("ts")) {
                ts = value;
            } else if (key.equals("h1")) {
                byte[] decoded = decodeHex(value);
                if (decoded != null) {
                    received[candidates++] = decoded;
                }
            }
        }
        if (ts == null || ts.isEmpty() || candidates == 0 || !isDigits(ts)) {
            return false;
        }
        if (clock != null) {
            long eventTs;
            try {
                eventTs = Long.parseLong(ts);
            } catch (NumberFormatException e) {
                return false;
            }
            long now = clock.instant().getEpochSecond();
            if (eventTs > now || now - eventTs > maxAgeSeconds) {
                return false;
            }
        }

        byte[] signedPayload = buildSignedPayload(ts, rawBody);
        byte[] expected = hmacSha256(signedPayload, secret.getBytes(StandardCharsets.UTF_8));

        boolean anyMatch = false;
        for (int i = 0; i < candidates; i++) {
            // Evaluate every candidate; no early exit so timing does not depend on which matched.
            anyMatch |= MessageDigest.isEqual(expected, received[i]);
        }
        return anyMatch;
    }

    static byte[] buildSignedPayload(String ts, byte[] rawBody) {
        byte[] prefix = (ts + ":").getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[prefix.length + rawBody.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(rawBody, 0, payload, prefix.length, rawBody.length);
        return payload;
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < '0' || s.charAt(i) > '9') {
                return false;
            }
        }
        return true;
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
        if (s.isEmpty() || (s.length() & 1) == 1) {
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
