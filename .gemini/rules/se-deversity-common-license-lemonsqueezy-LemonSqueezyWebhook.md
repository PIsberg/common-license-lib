<!-- VIBETAGS-START -->
# Rules for LemonSqueezyWebhook

## Context & Focus
- **Focus**: Constant-time comparison. Digest equality goes through MessageDigest.isEqual and nothing else.
- **Avoid**: String#equals, Arrays#equals, Objects#equals on digests; early-return on first mismatching byte

## Security Audit Requirements
When modifying this element, audit for:
- Timing attack
- Authentication Bypass

## Public API Surface Protection
- **Rule**: Exposes public API. Preserve signature, Javadoc, and behavior without breaking backwards or source compatibility.

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: webhook signature verification
<!-- VIBETAGS-END -->
