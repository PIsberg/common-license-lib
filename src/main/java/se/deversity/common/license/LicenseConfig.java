package se.deversity.common.license;

import se.deversity.common.license.email.AllowListEmailClassifier;
import se.deversity.common.license.email.EmailClassifier;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable configuration for a {@link LicenseGate}.
 *
 * <p>Multi-tenant by consumer: each app that embeds this library supplies its own
 * Keygen account + (optionally) LemonSqueezy store. There is no global state, so
 * the same JVM can run multiple {@link LicenseGate}s against different accounts.
 *
 * <p>Instances are built via {@link #builder()} and are safe to share across threads.
 * {@link #toString()} redacts credentials.
 */
public final class LicenseConfig {

    private final String keygenAccountId;
    private final String keygenApiKey;
    private final String keygenProductId;
    private final URI keygenBaseUri;
    private final Duration keygenTimeout;

    private final String lemonSqueezyStoreSubdomain;
    private final String lemonSqueezySigningSecret;

    private final EmailClassifier emailClassifier;
    private final HttpClient httpClient;
    private final boolean allowOnNetworkError;

    private LicenseConfig(Builder b) {
        this.keygenAccountId = b.keygenAccountId;
        this.keygenApiKey = b.keygenApiKey;
        this.keygenProductId = b.keygenProductId;
        this.keygenBaseUri = b.keygenBaseUri;
        this.keygenTimeout = b.keygenTimeout;

        this.lemonSqueezyStoreSubdomain = b.lemonSqueezyStoreSubdomain;
        this.lemonSqueezySigningSecret = b.lemonSqueezySigningSecret;

        this.emailClassifier = b.emailClassifier != null
            ? b.emailClassifier
            : new AllowListEmailClassifier(b.additionalFreeProviders, b.additionalCommercialProviders);

        this.httpClient = b.httpClient;
        this.allowOnNetworkError = b.allowOnNetworkError;
    }

    public String keygenAccountId()            { return keygenAccountId; }
    public String keygenApiKey()               { return keygenApiKey; }
    public String keygenProductId()            { return keygenProductId; }
    public URI    keygenBaseUri()              { return keygenBaseUri; }
    public Duration keygenTimeout()            { return keygenTimeout; }
    public String lemonSqueezyStoreSubdomain() { return lemonSqueezyStoreSubdomain; }
    public String lemonSqueezySigningSecret()  { return lemonSqueezySigningSecret; }
    public EmailClassifier emailClassifier()   { return emailClassifier; }
    public HttpClient httpClient()             { return httpClient; }
    public boolean allowOnNetworkError()       { return allowOnNetworkError; }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "LicenseConfig{"
            + "keygenAccountId=" + keygenAccountId
            + ", keygenApiKey=***"
            + ", keygenProductId=" + keygenProductId
            + ", keygenBaseUri=" + keygenBaseUri
            + ", lemonSqueezyStoreSubdomain=" + lemonSqueezyStoreSubdomain
            + ", lemonSqueezySigningSecret=" + (lemonSqueezySigningSecret == null ? "null" : "***")
            + ", allowOnNetworkError=" + allowOnNetworkError
            + '}';
    }

    public static final class Builder {
        private String keygenAccountId;
        private String keygenApiKey;
        private String keygenProductId;
        private URI keygenBaseUri = URI.create("https://api.keygen.sh");
        private Duration keygenTimeout = Duration.ofSeconds(10);

        private String lemonSqueezyStoreSubdomain;
        private String lemonSqueezySigningSecret;

        private Set<String> additionalFreeProviders = Collections.emptySet();
        private Set<String> additionalCommercialProviders = Collections.emptySet();
        private EmailClassifier emailClassifier;

        private HttpClient httpClient;
        private boolean allowOnNetworkError;

        private Builder() {
        }

        public Builder keygenAccountId(String v)    { this.keygenAccountId = v; return this; }
        public Builder keygenApiKey(String v)       { this.keygenApiKey = v; return this; }
        public Builder keygenProductId(String v)    { this.keygenProductId = v; return this; }
        public Builder keygenBaseUri(URI v)         { this.keygenBaseUri = Objects.requireNonNull(v); return this; }
        public Builder keygenTimeout(Duration v)    { this.keygenTimeout = Objects.requireNonNull(v); return this; }

        public Builder lemonSqueezyStoreSubdomain(String v) { this.lemonSqueezyStoreSubdomain = v; return this; }
        public Builder lemonSqueezySigningSecret(String v)  { this.lemonSqueezySigningSecret = v; return this; }

        /** Domains to treat as free providers in addition to the bundled list. */
        public Builder additionalFreeProviders(Set<String> v) {
            this.additionalFreeProviders = v == null ? Collections.emptySet() : Set.copyOf(v);
            return this;
        }

        /** Domains to force into the commercial bucket even if they're on the bundled free list. */
        public Builder additionalCommercialProviders(Set<String> v) {
            this.additionalCommercialProviders = v == null ? Collections.emptySet() : Set.copyOf(v);
            return this;
        }

        /** Replace the entire email classifier. Overrides the {@code additional*} setters. */
        public Builder emailClassifier(EmailClassifier v) {
            this.emailClassifier = v;
            return this;
        }

        /** Inject a preconfigured HTTP client (proxy, SSL, test doubles). */
        public Builder httpClient(HttpClient v) {
            this.httpClient = v;
            return this;
        }

        /**
         * When {@code true}, a Keygen network error yields
         * {@link LicenseResult.Allowed} (reason {@code NETWORK_ERROR_ALLOWED}) instead of
         * {@link LicenseResult.Denied}. Default {@code false} (fail-closed).
         */
        public Builder allowOnNetworkError(boolean v) {
            this.allowOnNetworkError = v;
            return this;
        }

        public LicenseConfig build() {
            if (keygenAccountId == null || keygenAccountId.isBlank()) {
                throw new LicenseException("keygenAccountId is required");
            }
            if (keygenApiKey == null || keygenApiKey.isBlank()) {
                throw new LicenseException("keygenApiKey is required");
            }
            return new LicenseConfig(this);
        }
    }
}
