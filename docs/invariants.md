# Design constraints worth preserving

Each of these is load-bearing. Breaking one is usually silent: the build stays green and the
gate quietly stops doing its job. Most are also declared on the code itself as VibeTags
annotations, so the generated guardrails in `CLAUDE.md` and `.claude/rules/` stay in step.

## No runtime dependencies

The jar depends on nothing but the JDK: `java.net.http.HttpClient` for transport and the
in-tree `internal.Json` for the three or four fields read out of Keygen's response. JUnit is
test-scope and the VibeTags processor is compile-scope with `RetentionPolicy.SOURCE`
annotations, so neither reaches consumers.

This is the reason `internal.Json` exists at all. Replacing it with Jackson or Gson would be
less code and would defeat the point.

Declared on `internal.Json` and `keygen.KeygenValidator` as `@AIContext(avoids = ...)`.

## Fail closed

A Keygen 5xx, a 401 from a bad API token, a timeout or an `IOException` all map to
`Denied(NETWORK_ERROR)`. Only `meta.valid=true` produces `Allowed`.

Fail-open is the consumer's decision, taken explicitly with
`LicenseConfig.Builder#allowOnNetworkError(true)`, and it is applied in `LicenseGate`, not in
`KeygenValidator`. Keep that split: the validator reports what happened, the gate decides what
it means.

Note that HTTP 200 with `meta.valid=false` is a normal denial, not an error. Keygen returns 200
for expired and suspended keys and puts the outcome in the body.

## Constant-time signature comparison

`LemonSqueezyWebhook.verifySignature` compares digests with
`MessageDigest.isEqual(byte[], byte[])`. `String#equals`, `Arrays#equals` and any loop that
returns early on the first mismatching byte leak the expected signature through timing.

The raw request bytes must be passed in. Re-serializing from a parsed JSON object changes
whitespace and the signature no longer matches.

Declared on `lemonsqueezy.LemonSqueezyWebhook` as `@AISecure` plus `@AIAudit`.

## Secrets never leak

`LicenseConfig.toString()` redacts `keygenApiKey` and `lemonSqueezySigningSecret`. Those two
fields carry `@AIPrivacy`. Keep them out of log statements, exception messages, test fixtures
and anything that gets serialized.

The signing secret is the whole of the webhook's security: anyone holding it can forge a
purchase event.

## Thread safety

`LicenseGate` is a single reusable instance per consumer, built once at startup. All its fields
are final, `LicenseConfig` is immutable, and `HttpClient` is itself thread-safe. Adding mutable
state, a per-call cache or a setter breaks the contract every consumer is relying on.

Declared as `@AIThreadSafe(IMMUTABLE)` on `LicenseGate` and `@AIImmutable` on `LicenseConfig`.

## Classification precedence

`AllowListEmailClassifier` unions `additionalFreeProviders` into the bundled list, then
subtracts `additionalCommercialProviders`. Commercial always wins. Swap the order and a domain
named in both lists silently becomes free, which is the failure that costs money.

Domains are normalized to lowercase and punycoded with `IDN.toASCII` so international domains
compare correctly.

## No global state

Every consumer supplies its own `LicenseConfig`. There are no singletons, no reflection and no
DI framework, so one JVM can run several `LicenseGate` instances against different Keygen
accounts at once. `LicenseGate.of(config)` returns a plain object.

## Errors versus outcomes

`LicenseException` is for programmer error: missing credentials, a malformed override list.
Operational outcomes, including invalid, expired and unknown licenses and network failures, come
back as `LicenseResult.Denied`. Do not start throwing on the hot path.
