# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- `LICENSE-COMMERCIAL.md` — draft terms for the commercial license the Noncommercial license
  points at. Tiers, term, scope and the redistribution/OEM case are written down; prices, support
  commitment and governing law are marked TBD and it has not had legal review.
- `docs/SELLING_THIS_LIBRARY.md` — the LemonSqueezy and Keygen dashboard setup needed to make the
  commercial license purchasable, and why the library deliberately does not gate itself.

### Removed
- The `Copyright Notice:` example block under *Notices* in `LICENSE`. The `Copyright Notice:` line
  at the top of the file is the one that governs; the example restated it and was a place for a
  stale address to hide.

### Changed
- **License: PolyForm Free Trial 1.0.0 to PolyForm Noncommercial 1.0.0.** Free Trial grants only
  32 days, and only for demonstration, testing and evaluation. That is the wrong basis for a
  library other projects depend on: `async-test-lib` is published under PolyForm Noncommercial
  and promises "free for non-commercial use", but pulls this library onto every consumer's
  runtime classpath, so its noncommercial users lost their license to the transitive dependency
  on day 33. Noncommercial has no time limit and matches the parent project, so the free tier is
  now coherent. Commercial use still requires a separate license, so nothing is given away.
  Applies from the next release; 0.2.0 and 0.2.1 remain published under Free Trial.

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
