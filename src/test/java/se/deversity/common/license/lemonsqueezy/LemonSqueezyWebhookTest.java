package se.deversity.common.license.lemonsqueezy;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class LemonSqueezyWebhookTest {

    // Reference vector: HMAC-SHA256("hello", "secret")
    // = 88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b
    private static final String REF_BODY   = "hello";
    private static final String REF_SECRET = "secret";
    private static final String REF_SIG    = "88aab3ede8d3adf94d26ab90d3bafd4a2083070c3bcce9c014ee04a443847c0b";

    @Test
    void verifiesKnownGoodSignature() {
        assertTrue(LemonSqueezyWebhook.verifySignature(
            REF_BODY.getBytes(StandardCharsets.UTF_8), REF_SIG, REF_SECRET));
    }

    @Test
    void verifiesKnownGoodSignatureCaseInsensitivelyForHex() {
        assertTrue(LemonSqueezyWebhook.verifySignature(
            REF_BODY.getBytes(StandardCharsets.UTF_8), REF_SIG.toUpperCase(), REF_SECRET));
    }

    @Test
    void rejectsTheRightDigestSpelledInNonAsciiDigits() {
        // Character.digit accepts fullwidth and other Unicode digits, so one signature had many
        // accepted spellings. A consumer de-duplicating deliveries by X-Signature could then be
        // fed the same event again under a "new" signature.
        StringBuilder fullwidth = new StringBuilder();
        for (char c : REF_SIG.toCharArray()) {
            fullwidth.append((char) (c <= '9' ? 0xFF10 + (c - '0') : 0xFF41 + (c - 'a')));
        }
        assertFalse(LemonSqueezyWebhook.verifySignature(
            REF_BODY.getBytes(StandardCharsets.UTF_8), fullwidth.toString(), REF_SECRET));
    }

    @Test
    void rejectsTamperedBody() {
        assertFalse(LemonSqueezyWebhook.verifySignature(
            "HELLO".getBytes(StandardCharsets.UTF_8), REF_SIG, REF_SECRET));
    }

    @Test
    void rejectsWrongSecret() {
        assertFalse(LemonSqueezyWebhook.verifySignature(
            REF_BODY.getBytes(StandardCharsets.UTF_8), REF_SIG, "wrong"));
    }

    @Test
    void rejectsMalformedSignatureHex() {
        assertFalse(LemonSqueezyWebhook.verifySignature(
            REF_BODY.getBytes(StandardCharsets.UTF_8), "zz", REF_SECRET));
        assertFalse(LemonSqueezyWebhook.verifySignature(
            REF_BODY.getBytes(StandardCharsets.UTF_8), "abc", REF_SECRET)); // odd length
    }

    @Test
    void rejectsNullInputs() {
        byte[] body = REF_BODY.getBytes(StandardCharsets.UTF_8);
        assertFalse(LemonSqueezyWebhook.verifySignature(null, REF_SIG, REF_SECRET));
        assertFalse(LemonSqueezyWebhook.verifySignature(body, null, REF_SECRET));
        assertFalse(LemonSqueezyWebhook.verifySignature(body, REF_SIG, null));
    }
}
