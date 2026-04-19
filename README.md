# common-license-lib

[![Maven Central](https://img.shields.io/maven-central/v/se.deversity.common/common-license-lib.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/se.deversity.common/common-license-lib)
![Java 21+](https://img.shields.io/badge/Java-21%2B-blue)
[![License: PolyForm Free Trial 1.0.0](https://img.shields.io/badge/License-PolyForm%20Free%20Trial%201.0.0-blue.svg)](https://polyformproject.org/licenses/free-trial/1.0.0/)

A drop-in **JVM license gate** designed for distributing software under
[PolyForm Commercial-style](https://polyformproject.org/licenses/) terms.

> **Free for personal use, paid for commercial use.**
> Users on common private-email providers (gmail, outlook, icloud, protonmail, …) are let
> through with no license key. Commercial email domains must present a valid license
> issued from *your* [Keygen.sh](https://keygen.sh) account.
> Sales flow via [LemonSqueezy](https://www.lemonsqueezy.com/) (checkout URL + webhook
> signature verification included).

## Install

### Maven

```xml
<dependency>
  <groupId>se.deversity.common</groupId>
  <artifactId>common-license-lib</artifactId>
  <version>0.1.0</version>
</dependency>
```

### Gradle (Kotlin DSL)

```kotlin
implementation("se.deversity.common:common-license-lib:0.1.0")
```

## Quick start

```java
import se.deversity.common.license.*;

LicenseConfig cfg = LicenseConfig.builder()
    .keygenAccountId("acct_xxx")
    .keygenApiKey(System.getenv("KEYGEN_API_KEY"))
    .keygenProductId("prod_xxx")                      // optional
    .lemonSqueezyStoreSubdomain("my-store")           // optional — for checkout URLs
    .lemonSqueezySigningSecret(System.getenv("LS_WEBHOOK_SECRET"))  // optional — for webhooks
    .build();

LicenseGate gate = LicenseGate.of(cfg);

LicenseResult r = gate.check(userEmail, /* licenseKey, may be null */ licenseKey);

switch (r) {
    case LicenseResult.Allowed a -> startApp();
    case LicenseResult.Denied d  -> showPaywall(d.reason(), gate.checkoutUrl(userEmail, variantId));
}
```

### Flow

1. Classify the email. If it's on the **free-provider allow-list**
   (gmail, outlook, hotmail, yahoo, icloud, protonmail, gmx, aol, …) → **Allowed**.
2. Otherwise the email is treated as commercial. If no license key is supplied →
   **Denied(LICENSE_REQUIRED)**.
3. Otherwise `POST` to Keygen's `validate-key` with `meta.scope.email` so the
   license is bound to the user → **Allowed** on `meta.valid=true`, else **Denied**
   with a reason code (`EXPIRED`, `SUSPENDED`, `INVALID`, `NOT_FOUND`).

Network errors default to **fail-closed** (`Denied(NETWORK_ERROR)`). Opt into
fail-open via `LicenseConfig.Builder#allowOnNetworkError(true)` if that's the
right tradeoff for your app.

## Configuration — multi-tenant by consumer

Every consumer app that embeds this library passes its own Keygen and
LemonSqueezy credentials via `LicenseConfig`. Nothing is hard-coded and there
is no shared global state — one consumer can run multiple `LicenseGate`
instances against different Keygen accounts in the same JVM.

### Extend the free-provider allow-list

```java
LicenseConfig.builder()
    .keygenAccountId(...)
    .keygenApiKey(...)
    .additionalFreeProviders(Set.of("my-intranet.example"))
    .additionalCommercialProviders(Set.of("example-contractor.com"))  // takes precedence
    .build();
```

Or plug in your own classifier:

```java
LicenseConfig.builder()
    .emailClassifier(email -> email.endsWith("@mycorp.com")
        ? EmailClassification.COMMERCIAL
        : EmailClassification.FREE_PROVIDER)
    ...
```

## LemonSqueezy helpers

**Build a pre-filled checkout URL** to send the user to:

```java
URI checkout = gate.checkoutUrl("user@corp.com", "VARIANT_ID");
```

**Verify an incoming webhook** in your server code:

```java
boolean ok = LemonSqueezyWebhook.verifySignature(
    rawBodyBytes,
    request.getHeader("X-Signature"),
    System.getenv("LS_WEBHOOK_SECRET"));
```

Signature comparison is constant-time (`MessageDigest.isEqual`), so it's safe
from timing attacks.

## Philosophy

- **No singletons.** `LicenseGate.of(config)` returns a plain instance.
- **No reflection, no DI framework.** Plain Java 21.
- **No runtime dependencies.** Uses `java.net.http.HttpClient` and a tiny
  purpose-built JSON parser for the handful of Keygen fields we read.
- **Secrets stay secret.** `LicenseConfig.toString()` redacts API keys.

## Build

```bash
./gradlew build      # or: mvn clean verify
```

## License

[PolyForm Free Trial License 1.0.0](https://polyformproject.org/licenses/free-trial/1.0.0/).

> **Note:** the *library itself* is published under PolyForm Free Trial for
> evaluation. Your downstream usage terms (what you gate with it in *your* app)
> are up to you — the library just provides the mechanism.
