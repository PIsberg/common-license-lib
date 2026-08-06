# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5.0] - 2026-08-06

### Added — Paddle as a second commerce provider, and operator-side Keygen issuance

Paddle Billing has no license engine of its own — no key generation, no validate endpoint — so
the design is *Paddle sells, Keygen licenses*: a buyer checks out through Paddle, and the seller
issues a Keygen license server-side for the same email. The existing `KeygenValidator` /
`Provider.KEYGEN` validation path is unchanged.

- `paddle.PaddleCheckout` — pure URL builder for Paddle's hosted checkout links
  (`https://pay.paddle.io/<hsc_...>` live, `sandbox-pay.paddle.io` sandbox), with `price_id` /
  `user_email` prefill. Mirrors `LemonSqueezyCheckout`; rejects `price_id`/`user_email` smuggled
  through `extraParams`.
- `paddle.PaddleWebhook` — verifies the `Paddle-Signature: ts=...;h1=...` header: HMAC-SHA256 over
  `ts:rawBody`, constant-time comparison against every `h1` rotation candidate with no early exit,
  plus an opt-in replay-window overload (`maxAgeSeconds`, `Clock`). Mirrors `LemonSqueezyWebhook`.
- `keygen.KeygenIssuer` — operator-side fulfilment: ensure a Keygen user exists for the buyer
  email (create, falling back to lookup-by-email on a duplicate), create a license under a
  policy, and return the key. Throws `LicenseException` on failure carrying the HTTP status and
  Keygen's error title only — never the raw response body or the admin token.

`PaddleCheckout`'s URL shape and `KeygenValidator`'s scope key were both corrected against live
accounts after this branch first went in:

- **Checkout URLs have no `/checkout/` path segment.** A hosted checkout created in a real Paddle
  sandbox produces `https://sandbox-pay.paddle.io/<hsc_...>`, not
  `https://pay.paddle.io/checkout/<hsc_...>` as the docs example implied. `PaddleCheckout` now
  takes `LIVE_BASE` or `SANDBOX_BASE` (any other base is rejected) and joins the id directly.
- **Keygen's validate-key scope key is `user`, not `email`.** Keygen rejects `scope.email` with
  HTTP 400 "unpermitted parameter" — it was never a documented scope, and loopback tests only
  pinned this library's own wire shape, so the bug was invisible until a real account existed.
  `KeygenValidator` now sends `scope.user` (Keygen resolves it from an email and enforces it: a
  wrong email yields `USER_SCOPE_MISMATCH`, a bogus key `NOT_FOUND`).
- **`KeygenValidator.apiKey` is now nullable.** `validate-key` is a public endpoint, but a
  made-up bearer token is rejected with 401 before evaluation — so a caller with no real token
  must send no `Authorization` header at all, not a placeholder value. `null` or blank now omits
  the header; the tokenless path still enforces the user scope.

## [0.4.0] - 2026-08-05

### Added — LemonSqueezy can now validate license keys, not just sell them

Before this release the LemonSqueezy integration could build a checkout URL and verify a webhook
signature, but `LicenseGate.check` had exactly one validation path: Keygen. A LemonSqueezy key
handed to the gate was posted to Keygen and denied. Selling on LemonSqueezy therefore required
standing up a webhook that provisioned a second license in Keygen, as
`docs/SELLING_THIS_LIBRARY.md` describes.

- `LemonSqueezyValidator` — online validation against
  [`POST /v1/licenses/validate`](https://docs.lemonsqueezy.com/api/license-api/validate-license-key).
  Fail-closed like `KeygenValidator`: transport failures, rate limits and 5xx map to
  `Denied(NETWORK_ERROR)`, never to an accidental pass.
- `LicenseConfig.Provider` — `KEYGEN` (default) or `LEMONSQUEEZY`. `LicenseGate` builds and
  consults only the selected provider's validator, and `build()` now validates only that
  provider's required inputs.
- `LicenseConfig.Builder.lemonSqueezyStoreId`, `lemonSqueezyProductId`,
  `lemonSqueezyEmailBinding`, `lemonSqueezyBaseUri` and `lemonSqueezyTimeout`.

**`lemonSqueezyStoreId` is mandatory for the LemonSqueezy provider, and the reason is a security
property of the endpoint.** `/v1/licenses/validate` carries no account credential — the license
key is the only input — so it answers for every key issued by every store on the platform. A
validator that trusted `valid: true` alone would accept a license bought from an unrelated
vendor. The store id is checked against `meta.store_id` on every success path, and a missing
`meta` is treated as no evidence and denied. `LemonSqueezyValidatorTest` pins this by asserting a
foreign `store_id` is rejected despite `valid: true`.

Two behaviours worth knowing, both pinned by tests:

- A never-activated key has status **`inactive`** and is **valid**. This validator never calls
  `/activate`, so every unused key is legitimately `inactive`; gating on `status == "active"`
  would reject them all.
- Email binding defaults to **`EmailBinding.DOMAIN`**, matching on the domain of
  `meta.customer_email`. Organisations buy once through a billing address and expect every
  developer on that domain to be covered, so exact-address matching would deny everyone except
  the person who paid. `EmailBinding.EXACT` remains available for per-seat licensing.

### Compatibility

Source- and binary-compatible for existing consumers. `Provider` defaults to `KEYGEN`, so a
config that names no provider behaves exactly as it did in 0.3.0, including the requirement for
`keygenAccountId` and `keygenApiKey`. The `LicenseGate` and `LicenseResult` public surfaces are
unchanged; the new denial cases reuse existing `DeniedReason` constants.


## [0.3.0] - 2026-08-02

First release under PolyForm Noncommercial. Versions 0.2.0 and 0.2.1 remain published under
PolyForm Free Trial and are not affected — Maven Central artifacts are immutable.

### Added
- VibeTags compile-time guardrails (`se.deversity.vibetags:vibetags-processor:1.0.0-RC8`,
  compile scope only). Annotations on the source declare the invariants that were previously
  only prose: fail-closed validation, constant-time signature comparison, secret redaction,
  thread-safety, and the `FreeProviders`/resource-file pair. The processor regenerates the
  managed region of `CLAUDE.md` and per-class rule files under `.claude/rules/`.
- `docs/build-and-test.md`, `docs/invariants.md`, `docs/common-tasks.md` and
  `docs/releasing.md`, so `CLAUDE.md` links to detail instead of restating it.
- `KeygenValidator` constructor overload taking a `productId`. The 5-argument constructor is
  retained and delegates with `productId = null`, so this is source- and binary-compatible.
- `LICENSE-COMMERCIAL.md` — draft terms for the commercial license the Noncommercial license
  points at. Tiers, term, scope and the redistribution/OEM case are written down; prices, support
  commitment and governing law are marked TBD and it has not had legal review.
- `docs/SELLING_THIS_LIBRARY.md` — the LemonSqueezy and Keygen dashboard setup needed to make the
  commercial license purchasable, and why the library deliberately does not gate itself.

### Changed
- **License: PolyForm Free Trial 1.0.0 to PolyForm Noncommercial 1.0.0.** Free Trial grants only
  32 days, and only for demonstration, testing and evaluation. That is the wrong basis for a
  library other projects depend on: `async-test-lib` is published under PolyForm Noncommercial
  and promises "free for non-commercial use", but pulls this library onto every consumer's
  runtime classpath, so its noncommercial users lost their license to the transitive dependency
  on day 33. Noncommercial has no time limit and matches the parent project, so the free tier is
  now coherent. Commercial use still requires a separate license, so nothing is given away.
  Applies from the next release; 0.2.0 and 0.2.1 remain published under Free Trial.
- JUnit 5.11.3 / 6.1.1 to 6.1.2 across the library, the consumer fixture and the examples.
- Gradle wrapper 8.13 to 9.6.1; `com.vanniktech.maven.publish` 0.30.0 to 0.37.0.
- CycloneDX Maven plugin 2.9.2 to 2.9.3; compiler plugin 3.13.0 to 3.15.0 and surefire 3.5.2
  to 3.5.6 in the consumer fixture and the example.
- pre-commit hooks: `pre-commit-java` v0.2.4 to v0.6.37, `gitleaks` v8.16.3 to v8.30.1,
  `pre-commit-hooks` v4.4.0 to v6.0.0.

### Fixed
- `CLAUDE.md` and `docs/architecture.md` described a testing approach the suite does not use.
  They claimed `mockMode(true)` and an injected `HttpClient`; both test classes actually drive a
  loopback `HttpServer` via `keygenBaseUri`. Neither of those two config options has any test
  coverage, which is now stated rather than implied.
- README install snippets pinned 0.1.0 while the project was at 0.2.1.
- `LicenseConfig.Builder#keygenProductId(...)` was accepted, stored and exposed via a getter, but
  never reached `KeygenValidator` — so validation was never scoped to a product and the README's
  "scope checks to this product" was false. A key issued for a different product of the same
  Keygen account validated successfully. It is now sent as `meta.scope.product`, covered by a
  wire-level test at both the validator and the `LicenseGate` level.

### Removed
- `.claude/common-license-lib.md`. It duplicated `CLAUDE.md`, was never loaded into context by
  any tool, and named the artifact `se.deversity.common:license-lib`, which does not exist. Its
  unique content moved into `docs/invariants.md` and `docs/common-tasks.md`.
- The `Copyright Notice:` example block under *Notices* in `LICENSE`. The `Copyright Notice:` line
  at the top of the file is the one that governs; the example restated it and was a place for a
  stale address to hide.

## [0.2.1] - 2026-04-19

Reconstructed from git history (tag `v0.2.1`); this release was published to Maven Central
without a changelog entry.

### Fixed
- Version conflicts between the library, the consumer fixture and `examples/minimal-gate`, which
  still referenced 0.1.0 after the 0.2.0 bump.
- Gradle build pinned to Java 21; Java 25 broke the build.
- `gradlew` was not executable, failing the GitHub Actions run.
- harden-runner egress policy blocked the Gradle distribution download.

## [0.2.0] - 2026-04-19

Reconstructed from git history (tag `v0.2.0`); this release was published to Maven Central
without a changelog entry.

### Added
- Maven Central publication via the Sonatype Central Portal: GPG signing, sources and javadoc
  jars, CycloneDX SBOM.
- StepSecurity hardening across the GitHub Actions workflows.
- `CLAUDE.md` and `docs/architecture.md` with PlantUML diagrams.

## [0.1.0] - 2026-04-19

### Added
- Initial release.
- `LicenseGate` primary entrypoint with email classification + Keygen online validation.
- `AllowListEmailClassifier` with a bundled list of ~45 common free-email providers.
  Consumer-supplied `additionalFreeProviders` / `additionalCommercialProviders` override or extend.
- `KeygenValidator` calling `POST /v1/accounts/{account}/licenses/actions/validate-key`,
  bound to the caller-supplied email via `meta.scope.email`.
- `LemonSqueezyCheckout.buildCheckoutUrl(...)` — signed, pre-filled checkout URLs.
- `LemonSqueezyWebhook.verifySignature(...)` — constant-time HMAC-SHA256 webhook signature
  verification.
- `LicenseConfig.Builder#allowOnNetworkError(boolean)` — opt-in fail-open on transient network errors.
- Dual build: Gradle (`build.gradle.kts`) + Maven (`pom.xml`), both publish to Maven Central Portal.
- Consumer-fixture Maven module exercising the public API end-to-end.
- `examples/minimal-gate` — 20-line demo.
