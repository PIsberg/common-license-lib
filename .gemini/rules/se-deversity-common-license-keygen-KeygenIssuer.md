<!-- VIBETAGS-START -->
# Rules for KeygenIssuer

## Context & Focus
- **Focus**: Operator-side only: the admin token must never appear in exception messages, toString or logs. Caller-supplied ids and emails are URL-encoded or JSON-escaped before use. Failures throw LicenseException with the HTTP status and Keygen's error title only.
- **Avoid**: Jackson, Gson, org.json, OkHttp, Apache HttpClient — the library ships zero runtime dependencies

## Security Audit Requirements
When modifying this element, audit for:
- Credential leakage in exception messages
- Server-side request forgery via caller-supplied ids

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: license issuance (admin-token API access)
<!-- VIBETAGS-END -->
