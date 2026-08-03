<!-- VIBETAGS-START -->
# Rules for KeygenValidator

## Context & Focus
- **Focus**: Fail closed. Every transport or authorization failure maps to Denied(NETWORK_ERROR); only meta.valid=true produces Allowed. Fail-open is the caller's decision, not this class's.
- **Avoid**: Jackson, Gson, org.json, OkHttp, Apache HttpClient — the library ships zero runtime dependencies

## Security Audit Requirements
When modifying this element, audit for:
- Authentication Bypass
- Credential leakage in exception messages

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: license validation
<!-- VIBETAGS-END -->
