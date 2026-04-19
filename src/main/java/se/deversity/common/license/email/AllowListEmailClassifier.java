package se.deversity.common.license.email;

import java.net.IDN;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Default {@link EmailClassifier}: an address is {@link EmailClassification#FREE_PROVIDER}
 * iff its normalized domain is in the effective free-provider set — the bundled list
 * unioned with {@code additionalFreeProviders} and with {@code additionalCommercialProviders}
 * subtracted (the commercial overrides always take precedence).
 *
 * <p>Normalization: lowercase, trim, strip a {@code +tag} from the local part (although
 * classification is domain-only), punycode IDN domains via {@link IDN#toASCII(String)}.
 */
public final class AllowListEmailClassifier implements EmailClassifier {

    private final Set<String> freeProviders;

    public AllowListEmailClassifier(Set<String> additionalFreeProviders,
                                    Set<String> additionalCommercialProviders) {
        Set<String> effective = new HashSet<>(FreeProviders.bundled());
        if (additionalFreeProviders != null) {
            for (String d : additionalFreeProviders) {
                if (d != null && !d.isBlank()) {
                    effective.add(d.trim().toLowerCase());
                }
            }
        }
        if (additionalCommercialProviders != null) {
            for (String d : additionalCommercialProviders) {
                if (d != null && !d.isBlank()) {
                    effective.remove(d.trim().toLowerCase());
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
        String domain = trimmed.substring(at + 1).toLowerCase();
        if (domain.contains("@") || domain.contains(" ")) {
            return null;
        }
        try {
            return IDN.toASCII(domain);
        } catch (IllegalArgumentException e) {
            return null;
        }
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
