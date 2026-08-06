package se.deversity.common.license.paddle;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PaddleWebhookTest {

    // Reference vector: HMAC-SHA256("1671552777:hello", "secret")
    // = 3f3a62ec599e093ab8d6b5a90a895ae5621cb6c785b9dfa1d322d51e5c83272a
    private static final String REF_TS     = "1671552777";
    private static final String REF_BODY   = "hello";
    private static final String REF_SECRET = "secret";
    private static final String REF_SIG    =
        "3f3a62ec599e093ab8d6b5a90a895ae5621cb6c785b9dfa1d322d51e5c83272a";
    private static final String REF_HEADER = "ts=" + REF_TS + ";h1=" + REF_SIG;

    private static byte[] body() {
        return REF_BODY.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void verifiesKnownGoodSignature() {
        assertTrue(PaddleWebhook.verifySignature(body(), REF_HEADER, REF_SECRET));
    }

    @Test
    void verifiesWhenAnyRotationCandidateMatches() {
        String rotated = "ts=" + REF_TS + ";h1=" + "00".repeat(32) + ";h1=" + REF_SIG;
        assertTrue(PaddleWebhook.verifySignature(body(), rotated, REF_SECRET));
    }

    @Test
    void toleratesWhitespaceAroundPairs() {
        String spaced = "ts=" + REF_TS + "; h1=" + REF_SIG;
        assertTrue(PaddleWebhook.verifySignature(body(), spaced, REF_SECRET));
    }

    @Test
    void rejectsTamperedBody() {
        assertFalse(PaddleWebhook.verifySignature(
            "HELLO".getBytes(StandardCharsets.UTF_8), REF_HEADER, REF_SECRET));
    }

    @Test
    void rejectsTamperedTimestamp() {
        String header = "ts=1671552778;h1=" + REF_SIG;
        assertFalse(PaddleWebhook.verifySignature(body(), header, REF_SECRET));
    }

    @Test
    void rejectsWrongSecret() {
        assertFalse(PaddleWebhook.verifySignature(body(), REF_HEADER, "wrong"));
    }

    @Test
    void rejectsHeaderWithoutTimestampOrSignature() {
        assertFalse(PaddleWebhook.verifySignature(body(), "h1=" + REF_SIG, REF_SECRET));
        assertFalse(PaddleWebhook.verifySignature(body(), "ts=" + REF_TS, REF_SECRET));
        assertFalse(PaddleWebhook.verifySignature(body(), "", REF_SECRET));
        assertFalse(PaddleWebhook.verifySignature(body(), "ts=notanumber;h1=" + REF_SIG, REF_SECRET));
    }

    @Test
    void rejectsMalformedSignatureHex() {
        assertFalse(PaddleWebhook.verifySignature(body(), "ts=" + REF_TS + ";h1=zz", REF_SECRET));
        assertFalse(PaddleWebhook.verifySignature(body(), "ts=" + REF_TS + ";h1=abc", REF_SECRET));
    }

    @Test
    void rejectsNullInputs() {
        assertFalse(PaddleWebhook.verifySignature(null, REF_HEADER, REF_SECRET));
        assertFalse(PaddleWebhook.verifySignature(body(), null, REF_SECRET));
        assertFalse(PaddleWebhook.verifySignature(body(), REF_HEADER, null));
    }

    @Test
    void replayCheckAcceptsFreshEvent() {
        Clock now = Clock.fixed(Instant.ofEpochSecond(1671552777L + 30), ZoneOffset.UTC);
        assertTrue(PaddleWebhook.verifySignature(body(), REF_HEADER, REF_SECRET, 60, now));
    }

    @Test
    void replayCheckRejectsStaleEvent() {
        Clock now = Clock.fixed(Instant.ofEpochSecond(1671552777L + 120), ZoneOffset.UTC);
        assertFalse(PaddleWebhook.verifySignature(body(), REF_HEADER, REF_SECRET, 60, now));
    }

    @Test
    void replayCheckRejectsFutureEvent() {
        Clock now = Clock.fixed(Instant.ofEpochSecond(1671552777L - 5), ZoneOffset.UTC);
        assertFalse(PaddleWebhook.verifySignature(body(), REF_HEADER, REF_SECRET, 60, now));
    }

    @Test
    void replayCheckRejectsInvalidArguments() {
        Clock now = Clock.fixed(Instant.ofEpochSecond(1671552777L), ZoneOffset.UTC);
        assertFalse(PaddleWebhook.verifySignature(body(), REF_HEADER, REF_SECRET, -1, now));
        assertFalse(PaddleWebhook.verifySignature(body(), REF_HEADER, REF_SECRET, 60, null));
    }
}
