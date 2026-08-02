# Common tasks

Recipes for the changes this repository actually sees. Read
[docs/invariants.md](invariants.md) first if the change touches classification, network
handling or signatures.

## Add a domain to the bundled free-provider list

Edit `src/main/resources/se/deversity/common/license/free-providers.txt`. One lowercase domain
per line; `#` starts a comment and blank lines are ignored.

The path is mirrored by the `RESOURCE` constant in `email/FreeProviders.java`, which carries an
`@AIKeepInSync` annotation naming it. Renaming or moving either one alone makes `loadBundled()`
throw during class initialization, which takes down every gate in the process.
`AllowListEmailClassifierTest#bundledSetIsNonTrivial` is what catches it.

## Let a consumer override classification

Nothing to change in the library. Consumers either extend the lists:

```java
LicenseConfig.builder()
    .additionalFreeProviders(Set.of("my-intranet.example"))
    .additionalCommercialProviders(Set.of("example-contractor.com"))  // wins over the line above
    .build();
```

or replace the strategy entirely with a lambda, since `EmailClassifier` is a
`@FunctionalInterface`:

```java
LicenseConfig.builder()
    .emailClassifier(email -> email.endsWith("@mycorp.com")
        ? EmailClassification.COMMERCIAL
        : EmailClassification.FREE_PROVIDER)
    .build();
```

`EmailClassifier#classify` carries `@AIContract`: its signature is frozen because every
consumer lambda binds to it.

## Add a denial reason

1. Add the constant to `LicenseResult.DeniedReason`.
2. Map the Keygen response code to it in `KeygenValidator.mapResponse`.
3. Cover it in `KeygenValidatorTest` with a canned response from the loopback server.

`LicenseResult` is `@AIPublicAPI` and consumers switch exhaustively over it. Adding a constant
is additive; removing or renaming one breaks their compile.

## Add or change a VibeTags guardrail

Edit the annotation on the Java element, then rebuild. Never hand-edit inside
`<!-- VIBETAGS-START -->` / `<!-- VIBETAGS-END -->` in `CLAUDE.md`, and never hand-edit the
files in `.claude/rules/`: the next compile overwrites both. Prose outside the markers survives.

Annotations currently in use, and what each one is asserting, are listed in
[docs/invariants.md](invariants.md).

## Bump a dependency

Versions live in four files and Dependabot only watches two of them:

| File | What it pins |
| :--- | :--- |
| `pom.xml` | JUnit, every Maven plugin, the VibeTags processor |
| `build.gradle.kts` | JUnit, VibeTags, the `com.vanniktech.maven.publish` plugin |
| `consumer-fixture/pom.xml` | Its own JUnit and plugin versions, plus `common-license-lib.version` |
| `examples/minimal-gate/pom.xml` | Same again |

`.github/dependabot.yml` covers the `maven` ecosystem at `/` and GitHub Actions. The two
downstream modules are not in its `directory` list, so their versions drift until someone
bumps them by hand.

Keep the JUnit version identical across all four. A mismatch between the Gradle and Maven test
runtimes produces failures that only reproduce in one build.

## Cut a release

See [docs/releasing.md](releasing.md).
