---
paths: ["**/PaddleWebhook.java"]
---

<!-- VIBETAGS-START -->
# Rules for PaddleWebhook

## Context & Focus
- **Focus**: Constant-time comparison against every h1 candidate — evaluate all candidates, never early-return between them on partial match. Replay protection is opt-in via the maxAgeSeconds overload; the two-argument form documents that it does not check ts.
- **Avoid**: String#equals, Arrays#equals, Objects#equals on digests; parsing the body before verification; any JSON dependency

## Security Audit Requirements
When modifying this element, audit for:
- Timing attack
- Authentication Bypass
- Replay attack

## Public API Surface Protection
- **Rule**: Exposes public API. Preserve signature, Javadoc, and behavior without breaking backwards or source compatibility.

## Security-Critical Code
- **Rule**: This code is security-critical. Do not weaken security properties. Every change must be explicitly reviewed for security impact.
- **Aspect**: webhook signature verification
<!-- VIBETAGS-END -->
