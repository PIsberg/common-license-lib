# Selling this library

How to stand up the purchase path for `common-license-lib`'s own commercial license, using the
same two services the library integrates with. The terms being sold live in
[LICENSE-COMMERCIAL.md](../LICENSE-COMMERCIAL.md).

Everything here is dashboard work in your own accounts. None of it can be scripted from this
repository, and nothing in the library's build or runtime depends on it.

## Why not gate the library with itself

The obvious move is to have `LicenseGate.of(...)` validate a key for the library itself. Don't.

The gate classifies *end users* by email domain. A library has no end user at the point it is
consumed — it has a developer running a build, and a build has no email to classify. The only
signal available would be a key present or absent, which means every noncommercial user would
have to obtain a key to compile, and the free tier established in
[LICENSE](../LICENSE) would stop being free in practice.

Enforcement here is legal, not technical. That is the normal arrangement for open-core libraries,
and it is why the commercial license is a document rather than a code path.

## 1. LemonSqueezy — the product

1. **Store → Products → New Product.**
   - Name: `common-license-lib — Commercial License`
   - Description: link to `LICENSE-COMMERCIAL.md` on GitHub so the terms are visible pre-purchase.
2. **Add one variant per tier** from the table in `LICENSE-COMMERCIAL.md`:
   - `Single product` — annual subscription
   - `Company` — annual subscription
   - `Redistribution / OEM` — annual subscription
3. Set each variant to **subscription, yearly**, so the term matches the license text.
4. Copy each **Variant ID** (Products → your product → Variants → Copy ID). These go into the
   buy links in `LICENSE-COMMERCIAL.md`.
5. **Settings → Webhooks → New webhook.**
   - URL: your endpoint (see step 3 below)
   - Events: at minimum `order_created` and `subscription_created`
   - Signing secret: generate one and store it as `LS_WEBHOOK_SECRET`

## 2. Keygen — the licenses you issue

1. **Products → New Product**, named `common-license-lib`. Copy the product ID.
2. **Policies → New Policy**, one per tier. Set expiry to 1 year to match the term.
3. **Settings → Tokens** — generate an environment token. Store it as `KEYGEN_API_KEY`.
4. Note the **Account ID** from Account Settings.

Keep the product ID: `LicenseConfig.Builder#keygenProductId(...)` scopes validation to it, so a
key issued under a different product of the same account is rejected.

## 3. The webhook endpoint

On purchase, LemonSqueezy POSTs to your endpoint. Verify the signature, then create the Keygen
license and mail the key to the buyer.

```java
// Verify first — the raw body, before any parsing or re-encoding.
boolean ok = LemonSqueezyWebhook.verifySignature(
    rawBodyBytes,
    request.getHeader("X-Signature"),
    System.getenv("LS_WEBHOOK_SECRET"));

if (!ok) {
    return 401;   // do not process, do not log the body
}

// Then create the Keygen license under the policy matching the purchased variant,
// and email the key to the buyer's address.
```

`verifySignature` compares with `MessageDigest.isEqual`, so it is constant-time. Pass the raw
bytes exactly as received — a JSON round-trip changes the digest and every valid webhook will
fail verification.

## 4. Fill in the buy links

Back in `LICENSE-COMMERCIAL.md`, replace the `TBD` under *How to buy* with one link per tier:

```
https://<your-store>.lemonsqueezy.com/buy/<VARIANT_ID>
```

`LemonSqueezyCheckout.buildCheckoutUrl(...)` builds the same URL pre-filled with the buyer's
email, if you would rather link from an app than from the document.

## Remaining decisions

These block the document from being a real offer:

- Prices for the three tiers
- Whether the license includes support, and with what response time
- Governing law
- A legal review of `LICENSE-COMMERCIAL.md`
