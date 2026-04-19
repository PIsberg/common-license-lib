# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with this repository.

## Build & Test

Both Gradle and Maven are supported. Gradle is the primary build tool; Maven is provided for consumer compatibility testing.

```bash
# Build and test
./gradlew build

# Run tests only
./gradlew test

# Run a single test class
./gradlew test --tests "se.deversity.common.license.LicenseGateTest"

# Run a single test method
./gradlew test --tests "se.deversity.common.license.LicenseGateTest#someMethod"

# Generate coverage report (also runs automatically after test)
./gradlew jacocoTestReport

# Maven alternative
mvn clean verify
```

The `consumer-fixture/` module is a separate Maven project that smoke-tests the library as an external consumer would use it. Run it independently with `mvn test` inside that directory.

## Architecture

This is a zero-dependency JVM library (Java 21, `java.net.http.HttpClient` only). The public API lives entirely in `src/main/java/se/deversity/common/license/`.

**Entry point flow:**

```
LicenseGate.check(email, licenseKey)
  → AllowListEmailClassifier (delegates to EmailClassifier)
      → FreeProviders (bundled list: gmail, outlook, icloud, protonmail, …)
  → if commercial: KeygenValidator.validate(licenseKey, email)
      → HTTP POST to Keygen API
      → Json (purpose-built parser, no external deps)
  → LicenseResult.Allowed | LicenseResult.Denied
```

**Key types:**
- `LicenseConfig` — immutable value object, builder pattern. Holds Keygen and LemonSqueezy credentials. Has a `mockMode()` flag that bypasses HTTP calls (useful in tests).
- `LicenseGate` — main entry point; plain instance, no singletons.
- `LicenseResult` — sealed interface with `Allowed`/`Denied` subtypes; `DeniedReason` enum covers `LICENSE_REQUIRED`, `EXPIRED`, `SUSPENDED`, `INVALID`, `NOT_FOUND`, `NETWORK_ERROR`.
- `EmailClassifier` — interface; default impl is `AllowListEmailClassifier`. Consumers can supply their own via `LicenseConfig.Builder#emailClassifier(...)`.
- `LemonSqueezyCheckout` / `LemonSqueezyWebhook` — standalone helpers (no `LicenseGate` dependency needed for webhook verification).
- `Json` — internal-only minimal JSON parser; do not expose in public API.

**Package structure:**
```
license/             ← public API (LicenseGate, LicenseConfig, LicenseResult, LicenseException)
license/email/       ← email classification (EmailClassifier interface + AllowListEmailClassifier)
license/keygen/      ← Keygen HTTP integration
license/lemonsqueezy/← checkout URL builder + webhook signature verification
license/internal/    ← Json parser (internal, not public API)
```

**Testing approach:** Tests use JUnit 5. `KeygenValidatorTest` and `LicenseGateTest` use `LicenseConfig.mockMode(true)` or inject a custom `HttpClient` to avoid real network calls. `consumer-fixture/` tests the published JAR via Maven as an end-to-end integration check.

## Publishing

Published to Maven Central as `se.deversity.common:common-license-lib`. Version is set in `gradle.properties`. Uses the `com.vanniktech.maven.publish` plugin; signing requires `signingInMemoryKey` Gradle property.
