---
paths: ["**/LicenseConfig.java"]
---

<!-- VIBETAGS-START -->
# Rules for LicenseConfig

## PII / Privacy Guardrails
- **Rule**: Never log or expose runtime values of these elements.

### Rules for field keygenApiKey
- **Reason**: Keygen bearer token. toString() redacts it deliberately; keep it that way.

### Rules for field lemonSqueezySigningSecret
- **Reason**: LemonSqueezy webhook signing secret; holding it is enough to forge a purchase event.

## Immutable Type
- **Rule**: This type is immutable. Never introduce non-final fields, setters, or mutating methods.
- **Note**: Every field is final and the instance is shared across threads by LicenseGate. Never add a setter or a non-final field — add a builder method instead.

## Public API Surface Protection
- **Rule**: Exposes public API. Preserve signature, Javadoc, and behavior without breaking backwards or source compatibility.
- **Reason**: Published builder API; consumer code constructs this directly.
<!-- VIBETAGS-END -->
