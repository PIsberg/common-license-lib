package se.deversity.common.license;

import se.deversity.common.license.email.AllowListEmailClassifier;
import se.deversity.common.license.email.EmailClassifier;
import se.deversity.common.license.lemonsqueezy.LemonSqueezyValidator;

import se.deversity.vibetags.annotations.AIImmutable;
import se.deversity.vibetags.annotations.AIPrivacy;
import se.deversity.vibetags.annotations.AIPublicAPI;

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
@AIPublicAPI(reason = "Published builder API; consumer code constructs this directly.")
@AIImmutable(note = "Every field is final and the instance is shared across threads by LicenseGate. "
    + "Never add a setter or a non-final field — add a builder method instead.")
public final class LicenseConfig {

    /**
     * Which service issues and validates the license keys this app accepts.
     *
     * <p>One gate validates against one provider. The two have different trust models:
     * Keygen authenticates the caller with a bearer token and scopes server-side, whereas
     * LemonSqueezy's validate endpoint is unauthenticated and global, so its scoping is
     * enforced client-side from {@code meta} (see {@code LemonSqueezyValidator}).
     */
    public enum Provider {
        /** Validate keys against Keygen.sh. The default, and the only option before 0.4.0. */
        KEYGEN,
        /** Validate keys against LemonSqueezy's License API. */
        LEMONSQUEEZY
    }

    private final Provider licenseProvider;

    private final String keygenAccountId;
    @AIPrivacy(reason = "Keygen bearer token. toString() redacts it deliberately; keep it that way.")
    private final String keygenApiKey;
    private final String keygenProductId;
    private final URI keygenBaseUri;
    private final Duration keygenTimeout;

    private final String lemonSqueezyStoreSubdomain;
    @AIPrivacy(reason = "LemonSqueezy webhook signing secret; holding it is enough to forge a purchase event.")
    private final String lemonSqueezySigningSecret;
    private final Long lemonSqueezyStoreId;
    private final Long lemonSqueezyProductId;
    private final LemonSqueezyValidator.EmailBinding lemonSqueezyEmailBinding;
    private final URI lemonSqueezyBaseUri;
    private final Duration lemonSqueezyTimeout;

    private final EmailClassifier emailClassifier;
    private final HttpClient httpClient;
    private final boolean allowOnNetworkError;
    private final boolean mockMode;

    private LicenseConfig(Builder b) {
        this.licenseProvider = b.licenseProvider;

        this.keygenAccountId = b.keygenAccountId;
        this.keygenApiKey = b.keygenApiKey;
        this.keygenProductId = b.keygenProductId;
        this.keygenBaseUri = b.keygenBaseUri;
        this.keygenTimeout = b.keygenTimeout;

        this.lemonSqueezyStoreSubdomain = b.lemonSqueezyStoreSubdomain;
        this.lemonSqueezySigningSecret = b.lemonSqueezySigningSecret;
        this.lemonSqueezyStoreId = b.lemonSqueezyStoreId;
        this.lemonSqueezyProductId = b.lemonSqueezyProductId;
        this.lemonSqueezyEmailBinding = b.lemonSqueezyEmailBinding;
        this.lemonSqueezyBaseUri = b.lemonSqueezyBaseUri;
        this.lemonSqueezyTimeout = b.lemonSqueezyTimeout;

        this.emailClassifier = b.emailClassifier != null
            ? b.emailClassifier
            : new AllowListEmailClassifier(b.additionalFreeProviders, b.additionalCommercialProviders);

        this.httpClient = b.httpClient;
        this.allowOnNetworkError = b.allowOnNetworkError;
        this.mockMode = b.mockMode;
    }

    public Provider licenseProvider()          { return licenseProvider; }
    public String keygenAccountId()            { return keygenAccountId; }
    public String keygenApiKey()               { return keygenApiKey; }
    public String keygenProductId()            { return keygenProductId; }
    public URI    keygenBaseUri()              { return keygenBaseUri; }
    public Duration keygenTimeout()            { return keygenTimeout; }
    public String lemonSqueezyStoreSubdomain() { return lemonSqueezyStoreSubdomain; }
    public String lemonSqueezySigningSecret()  { return lemonSqueezySigningSecret; }
    public Long   lemonSqueezyStoreId()        { return lemonSqueezyStoreId; }
    public Long   lemonSqueezyProductId()      { return lemonSqueezyProductId; }
    public LemonSqueezyValidator.EmailBinding lemonSqueezyEmailBinding() { return lemonSqueezyEmailBinding; }
    public URI    lemonSqueezyBaseUri()        { return lemonSqueezyBaseUri; }
    public Duration lemonSqueezyTimeout()      { return lemonSqueezyTimeout; }
    public EmailClassifier emailClassifier()   { return emailClassifier; }
    public HttpClient httpClient()             { return httpClient; }
    public boolean allowOnNetworkError()       { return allowOnNetworkError; }
    public boolean mockMode()                  { return mockMode; }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "LicenseConfig{"
            + "licenseProvider=" + licenseProvider
            + ", keygenAccountId=" + keygenAccountId
            + ", keygenApiKey=***"
            + ", keygenProductId=" + keygenProductId
            + ", keygenBaseUri=" + keygenBaseUri
            + ", lemonSqueezyStoreSubdomain=" + lemonSqueezyStoreSubdomain
            + ", lemonSqueezySigningSecret=" + (lemonSqueezySigningSecret == null ? "null" : "***")
            + ", lemonSqueezyStoreId=" + lemonSqueezyStoreId
            + ", lemonSqueezyProductId=" + lemonSqueezyProductId
            + ", lemonSqueezyBaseUri=" + lemonSqueezyBaseUri
            + ", allowOnNetworkError=" + allowOnNetworkError
            + '}';
    }

    public static final class Builder {
        private Provider licenseProvider = Provider.KEYGEN;

        private String keygenAccountId;
        private String keygenApiKey;
        private String keygenProductId;
        private URI keygenBaseUri = URI.create("https://api.keygen.sh");
        private Duration keygenTimeout = Duration.ofSeconds(10);

        private String lemonSqueezyStoreSubdomain;
        private String lemonSqueezySigningSecret;
        private Long lemonSqueezyStoreId;
        private Long lemonSqueezyProductId;
        private LemonSqueezyValidator.EmailBinding lemonSqueezyEmailBinding =
            LemonSqueezyValidator.EmailBinding.DOMAIN;
        private URI lemonSqueezyBaseUri = LemonSqueezyValidator.DEFAULT_BASE_URI;
        private Duration lemonSqueezyTimeout = Duration.ofSeconds(10);

        private Set<String> additionalFreeProviders = Collections.emptySet();
        private Set<String> additionalCommercialProviders = Collections.emptySet();
        private EmailClassifier emailClassifier;

        private HttpClient httpClient;
        private boolean allowOnNetworkError;
        private boolean mockMode;

        private Builder() {
        }

        /**
         * Which service validates license keys. Defaults to {@link Provider#KEYGEN}, which is the
         * behaviour of every release before 0.4.0.
         */
        public Builder licenseProvider(Provider v) {
            this.licenseProvider = Objects.requireNonNull(v, "licenseProvider");
            return this;
        }

        public Builder keygenAccountId(String v)    { this.keygenAccountId = v; return this; }
        public Builder keygenApiKey(String v)       { this.keygenApiKey = v; return this; }
        public Builder keygenProductId(String v)    { this.keygenProductId = v; return this; }
        public Builder keygenBaseUri(URI v)         { this.keygenBaseUri = Objects.requireNonNull(v); return this; }
        public Builder keygenTimeout(Duration v)    { this.keygenTimeout = Objects.requireNonNull(v); return this; }

        public Builder lemonSqueezyStoreSubdomain(String v) { this.lemonSqueezyStoreSubdomain = v; return this; }
        public Builder lemonSqueezySigningSecret(String v)  { this.lemonSqueezySigningSecret = v; return this; }

        /**
         * Numeric ID of the LemonSqueezy store whose keys this app accepts — the {@code store_id}
         * in a validate response, not the storefront subdomain.
         *
         * <p>Required when {@link Provider#LEMONSQUEEZY} is selected. LemonSqueezy's validate
         * endpoint takes no account credential and answers for every store on the platform, so
         * without this a license key bought from an unrelated vendor would be accepted.
         */
        public Builder lemonSqueezyStoreId(Long v) { this.lemonSqueezyStoreId = v; return this; }

        /** Optional narrower scope: reject keys issued for another product of the same store. */
        public Builder lemonSqueezyProductId(Long v) { this.lemonSqueezyProductId = v; return this; }

        /**
         * How the buyer's address is matched against the running user's. Defaults to
         * {@link LemonSqueezyValidator.EmailBinding#DOMAIN}, which is what a company licence needs:
         * one purchase by a billing address covers every developer on that domain. Switch to
         * {@code EXACT} only for per-seat licensing.
         */
        public Builder lemonSqueezyEmailBinding(LemonSqueezyValidator.EmailBinding v) {
            this.lemonSqueezyEmailBinding = Objects.requireNonNull(v, "lemonSqueezyEmailBinding");
            return this;
        }

        /** Override the License API host. Intended for tests. */
        public Builder lemonSqueezyBaseUri(URI v) {
            this.lemonSqueezyBaseUri = Objects.requireNonNull(v);
            return this;
        }

        /** Per-request timeout for the LemonSqueezy validate call. */
        public Builder lemonSqueezyTimeout(Duration v) {
            this.lemonSqueezyTimeout = Objects.requireNonNull(v);
            return this;
        }

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

        /**
         * When {@code true}, the gate returns {@link Allowed} (mocked) without network calls.
         */
        public Builder mockMode(boolean v) {
            this.mockMode = v;
            return this;
        }

        /**
         * Validates the inputs the selected provider actually needs. Mock mode relaxes both
         * branches, since no request is ever made.
         */
        public LicenseConfig build() {
            switch (licenseProvider) {
                case KEYGEN -> {
                    if (keygenAccountId == null || keygenAccountId.isBlank()) {
                        if (!mockMode) throw new LicenseException("keygenAccountId is required");
                        else keygenAccountId = "mocked";
                    }
                    if (keygenApiKey == null || keygenApiKey.isBlank()) {
                        if (!mockMode) throw new LicenseException("keygenApiKey is required");
                        else keygenApiKey = "mocked";
                    }
                }
                case LEMONSQUEEZY -> {
                    if (lemonSqueezyStoreId == null && !mockMode) {
                        throw new LicenseException(
                            "lemonSqueezyStoreId is required when licenseProvider is LEMONSQUEEZY: "
                                + "the validate endpoint is unauthenticated and answers for every "
                                + "store on the platform, so without a store scope a license key "
                                + "bought from any other vendor would be accepted");
                    }
                }
            }
            return new LicenseConfig(this);
        }
    }
}
