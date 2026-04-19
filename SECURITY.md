# Security Policy

## Supported Versions

The latest minor release on the `main` branch is supported. Older versions
receive fixes on a best-effort basis.

## Reporting a Vulnerability

Please report security issues privately via **GitHub Security Advisories**
(https://github.com/PIsberg/common-license-lib/security/advisories/new) or by email
to the maintainer listed in `pom.xml`.

Do **not** open public GitHub issues for security problems.

We aim to acknowledge reports within 3 business days and ship a fix or
mitigation within 30 days for confirmed vulnerabilities.

## Scope

In scope:
- Signature verification bypass in `LemonSqueezyWebhook.verifySignature`.
- License validation bypass that lets an invalid key pass `LicenseGate.check`.
- Credentials leaking via `toString()`, logs, or exception messages.
- Email classification bypass that incorrectly flags a commercial address as free.

Out of scope:
- The strength of Keygen's or LemonSqueezy's own APIs — report those to the
  respective vendors.
- Denial of service by supplying an extreme free-provider override list.
