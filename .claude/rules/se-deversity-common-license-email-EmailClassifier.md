---
paths: ["**/EmailClassifier.java"]
---

<!-- VIBETAGS-START -->
# Rules for EmailClassifier

### Rules for method classify
- **Constraint**: You may change internal logic, but MUST NOT modify the method name, parameters, return type, or checked exceptions.
- **Reason**: The single abstract method of a @FunctionalInterface. Consumers implement it with a lambda via LicenseConfig.Builder#emailClassifier, so any change to the name, parameter list or return type breaks every custom classifier at compile time.

## Public API Surface Protection
- **Rule**: Exposes public API. Preserve signature, Javadoc, and behavior without breaking backwards or source compatibility.
<!-- VIBETAGS-END -->
