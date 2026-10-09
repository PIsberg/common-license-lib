package se.deversity.common.license.email;

import java.net.IDN;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import se.deversity.vibetags.annotations.AIContext;

/**
 * Default {@link EmailClassifier}: an address is {@link EmailClassification#FREE_PROVIDER}
 * iff its normalized domain is in the effective free-provider set — the bundled list
 * unioned with {@code additionalFreeProviders} and with {@code additionalCommercialProviders}
 * subtracted (the commercial overrides always take precedence).
 *
 * <p>Normalization: trim, lowercase with {@link Locale#ROOT} (never the default locale), punycode
 * IDN domains via {@link IDN#toASCII(String)}. Classification is domain-only, so the local part
 * (including any {@code +tag}) is ignored. The {@code additional*} overrides are normalized the
 * same way, so {@code bücher.example} and {@code xn--bcher-kva.example} name the same domain.
 */
@AIContext(
    focus = "Precedence is load-bearing: additionalCommercialProviders is subtracted after "
        + "additionalFreeProviders is unioned in, so a domain named in both is COMMERCIAL. "
        + "Reordering those two steps silently gives paying domains a free pass.",
    avoids = "Regex-based email parsing; case-sensitive domain comparison; skipping IDN.toASCII normalisation"
)
public final class AllowListEmailClassifier implements EmailClassifier {

    private final Set<String> freeProviders;

    public AllowListEmailClassifier(Set<String> additionalFreeProviders,
                                    Set<String> additionalCommercialProviders) {
        Set<String> effective = new HashSet<>(FreeProviders.bundled());
        if (additionalFreeProviders != null) {
            for (String d : additionalFreeProviders) {
                if (d != null && !d.isBlank()) {
                    effective.add(normalizeOverride(d));
                }
            }
        }
        if (additionalCommercialProviders != null) {
            for (String d : additionalCommercialProviders) {
                if (d != null && !d.isBlank()) {
                    effective.remove(normalizeOverride(d));
                }
            }
        }
        this.freeProviders = Collections.unmodifiableSet(effective);
    }

    /** Effective free-provider set used by this classifier. Useful for diagnostics / tests. */
    public Set<String> effectiveFreeProviders() {
        return freeProviders;
    }

    @Override
    public EmailClassification classify(String email) {
        String domain = extractDomain(email);
        if (domain == null) {
            return EmailClassification.INVALID;
        }
        return freeProviders.contains(domain)
            ? EmailClassification.FREE_PROVIDER
            : EmailClassification.COMMERCIAL;
    }

    /** Returns the normalized, punycoded, lowercased domain, or {@code null} if malformed. */
    static String extractDomain(String email) {
        if (email == null) {
            return null;
        }
        String trimmed = email.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        int at = trimmed.lastIndexOf('@');
        if (at <= 0 || at == trimmed.length() - 1) {
            return null;
        }
        String domain = trimmed.substring(at + 1);
        if (domain.contains("@") || domain.contains(" ")) {
            return null;
        }
        return normalizeDomain(domain);
    }

    /**
     * Lowercases with {@link Locale#ROOT} and punycodes. The default locale must not take part:
     * under Turkish rules {@code "GMAIL.COM".toLowerCase()} is {@code "gmaıl.com"}.
     *
     * @return the normalized domain, or {@code null} if {@link IDN#toASCII(String)} rejects it
     */
    static String normalizeDomain(String domain) {
        try {
            return IDN.toASCII(domain.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Overrides go through the same normalization as addresses, so a domain spelled in Unicode
     * matches the punycoded form an address is looked up by. One that IDN rejects is kept as
     * given (lowercased) rather than dropped, which was the behaviour before normalization.
     */
    private static String normalizeOverride(String domain) {
        String n = normalizeDomain(domain);
        return n != null ? n : domain.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AllowListEmailClassifier other
            && Objects.equals(freeProviders, other.freeProviders);
    }

    @Override
    public int hashCode() {
        return freeProviders.hashCode();
    }
}
