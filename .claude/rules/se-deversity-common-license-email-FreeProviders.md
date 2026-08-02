---
paths: ["**/FreeProviders.java"]
---

<!-- VIBETAGS-START -->
# Rules for FreeProviders

### Rules for field RESOURCE
- **Rule**: Free to change, but every mirror must change in the same commit.
- **Mirrors**: src/main/resources/se/deversity/common/license/free-providers.txt
- **Reason**: The constant is the only reference to that classpath resource. Move or rename either one alone and loadBundled() throws at class-initialisation time, taking every gate down.
- **Enforced by**: AllowListEmailClassifierTest#bundledSetIsNonTrivial
<!-- VIBETAGS-END -->
