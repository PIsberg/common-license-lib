<!-- VIBETAGS-START -->
# Rules for AllowListEmailClassifier

## Context & Focus
- **Focus**: Precedence is load-bearing: additionalCommercialProviders is subtracted after additionalFreeProviders is unioned in, so a domain named in both is COMMERCIAL. Reordering those two steps silently gives paying domains a free pass.
- **Avoid**: Regex-based email parsing; case-sensitive domain comparison; skipping IDN.toASCII normalisation
<!-- VIBETAGS-END -->
