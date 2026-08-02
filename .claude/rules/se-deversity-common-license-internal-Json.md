---
paths: ["**/Json.java"]
---

<!-- VIBETAGS-START -->
# Rules for Json

## Context & Focus
- **Focus**: Internal, not public API. It exists only so the library can ship zero runtime dependencies while reading a handful of fields from Keygen's validate-key response. Keep it small; do not widen it into a general-purpose library or expose it from the license package.
- **Avoid**: Jackson, Gson, org.json, javax.json — adding any of them defeats the reason this class exists
<!-- VIBETAGS-END -->
