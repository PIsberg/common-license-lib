---
paths: ["**/LicenseGate.java"]
---

<!-- VIBETAGS-START -->
# Rules for LicenseGate

## Thread-Safety Guarantee
- **Strategy**: IMMUTABLE
- **Note**: One instance per consumer app, reused for the process lifetime. All fields are final and the shared HttpClient is itself thread-safe. Do not add mutable state or per-call caching.

## Public API Surface Protection
- **Rule**: Exposes public API. Preserve signature, Javadoc, and behavior without breaking backwards or source compatibility.
<!-- VIBETAGS-END -->
