# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
