# license-lib — Claude context pack

Terse reference for anyone (human or Claude) touching this repo.

## What it is

A JVM library published as `se.deversity.common:license-lib` (Java 21).
Drop-in license gate that:

1. **Classifies an email.** Common private-mail domains (gmail, outlook, icloud, …)
   pass without a license key. Everything else is commercial.
2. **Validates commercial users against Keygen.sh** via their
   `POST /v1/accounts/{account}/licenses/actions/validate-key` endpoint,
   scoped to `meta.scope.email`.
3. **Helps with LemonSqueezy** — builds pre-filled checkout URLs and verifies
   webhook HMAC-SHA256 signatures. Does not call the LS API for validation.

Multi-tenant by consumer: every app embedding the library passes its own
Keygen + LS credentials via `LicenseConfig`. No global state.

## Public API surface

- `LicenseGate` — primary entrypoint. `LicenseGate.of(config).check(email, key)`.
- `LicenseConfig` / `LicenseConfig.Builder` — immutable, secrets redacted in `toString`.
- `LicenseResult` — sealed: `Allowed(AllowedReason)` / `Denied(DeniedReason, String)`.
- `email.EmailClassifier`, `email.AllowListEmailClassifier`, `email.FreeProviders`.
- `keygen.KeygenValidator` — escape hatch if you want to bypass `LicenseGate`.
- `lemonsqueezy.LemonSqueezyCheckout` — URL builder.
- `lemonsqueezy.LemonSqueezyWebhook.verifySignature(body, header, secret)`.

Internal: `internal.Json` (tiny hand-rolled parser — don't depend on it from outside).

## Build

Dual-built. Both `mvn clean verify` and `./gradlew build` must pass.

```bash
mvn -Dmaven.repo.local=.m2/repository clean install
mvn -Dmaven.repo.local=.m2/repository -f consumer-fixture/pom.xml test
./gradlew --no-daemon build
```

## Design constraints worth preserving

- **No runtime dependencies.** `java.net.http.HttpClient` + an in-tree JSON parser.
  JUnit is test-scope only.
- **Fail-closed by default.** A Keygen HTTP 5xx / timeout / 401 becomes
  `Denied(NETWORK_ERROR)` unless the consumer explicitly sets
  `.allowOnNetworkError(true)`.
- **Signatures via `MessageDigest.isEqual`** — constant-time. Don't replace with `String#equals`.
- **Secrets never leak.** `LicenseConfig.toString` redacts; don't log raw config elsewhere.
- **Thread-safe.** `LicenseGate` is a single reusable instance per consumer.

## Common tasks

- **Add a free-provider domain to the bundled list:** edit
  `src/main/resources/se/deversity/common/license/free-providers.txt`.
- **Extend commercial detection logic:** plug in a custom `EmailClassifier` via
  `LicenseConfig.Builder#emailClassifier(...)`.
- **Add a new Keygen denial reason:** add enum in `LicenseResult.DeniedReason`,
  map in `KeygenValidator.mapResponse`, cover with test.
- **Release:** push a `vX.Y.Z` tag — `publish.yml` handles Maven Central + cosign.
