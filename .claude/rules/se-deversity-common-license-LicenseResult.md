---
paths: ["**/LicenseResult.java"]
---

<!-- VIBETAGS-START -->
# Rules for LicenseResult

## Public API Surface Protection
- **Rule**: Exposes public API. Preserve signature, Javadoc, and behavior without breaking backwards or source compatibility.
- **Reason**: Consumers switch exhaustively over this sealed hierarchy. Adding a permitted subtype, removing an enum constant, or renaming one is a source-breaking change for every caller.
<!-- VIBETAGS-END -->
