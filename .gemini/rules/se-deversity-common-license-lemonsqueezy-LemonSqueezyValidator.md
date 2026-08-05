<!-- VIBETAGS-START -->
# Rules for LemonSqueezyValidator

## Context & Focus
- **Focus**: Fail closed, and never trust `valid:true` on its own. The validate endpoint is unauthenticated and spans all of LemonSqueezy, so the store scope is the only thing separating our customers' keys from every other vendor's. Keep the store check on every success path.
- **Avoid**: Jackson, Gson, org.json, OkHttp, Apache HttpClient — the library ships zero runtime dependencies

## Security Audit Requirements
When modifying this element, audit for:
- Authentication Bypass
- Credential leakage in exception messages

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: license validation
<!-- VIBETAGS-END -->
