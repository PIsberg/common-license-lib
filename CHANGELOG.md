# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
