# Build and test

Both Gradle and Maven build this library, and both must stay green. Gradle is the primary
local build; Maven is what `publish.yml` deploys with, and it is also how downstream Maven
consumers see the project.

## Toolchain

| Thing | Version |
| :--- | :--- |
| Language level | Java 21 (`source`/`target` 21) |
| Gradle wrapper | 9.6.1 |
| JUnit | 6.1.2 |
| CI test matrix (Maven) | Java 21 and Java 25 |
| CI test matrix (Gradle) | Java 21 |

## Gradle

```bash
./gradlew build                 # compile, test, jacoco report
./gradlew test                  # tests only
./gradlew jacocoTestReport      # coverage (also runs automatically after test)

# one class
./gradlew test --tests "se.deversity.common.license.LicenseGateTest"
# one method
./gradlew test --tests "se.deversity.common.license.LicenseGateTest#freeProviderEmailIsLetThroughWithoutHittingKeygen"
```

## Maven

```bash
mvn clean verify
```

CI installs the artifact into a repo-local `.m2` first so the downstream modules resolve the
working tree rather than the last published release:

```bash
mvn -Dmaven.repo.local=.m2/repository clean install
mvn -Dmaven.repo.local=.m2/repository -f consumer-fixture/pom.xml test
mvn -Dmaven.repo.local=.m2/repository -f examples/minimal-gate/pom.xml test
```

Skipping the `install` step makes the fixture resolve `common-license-lib` from Maven Central,
so it can pass against a released jar while the working tree is broken.

## The downstream modules

`consumer-fixture/` and `examples/minimal-gate/` are separate Maven projects, not reactor
modules. They depend on the published coordinates and exist to catch what an in-tree test
cannot: a change that compiles here but breaks a consumer, or a public type that never made it
into the jar. Both pin `common-license-lib.version`, which has to move with every release.

## How the tests avoid the real Keygen API

No test reaches the public internet.

| Technique | Where |
| :--- | :--- |
| Loopback `com.sun.net.httpserver.HttpServer` on `127.0.0.1:0`, with `keygenBaseUri(...)` pointed at it | `KeygenValidatorTest`, `LicenseGateTest` |
| Pure unit tests, no I/O | `AllowListEmailClassifierTest`, `JsonTest`, `LemonSqueezyCheckoutTest`, `LemonSqueezyWebhookTest`, `LicenseConfigTest` |

The loopback server serves canned Keygen response bodies, so the tests exercise the real
`HttpClient` code path including timeouts and status mapping.

Two knobs on `LicenseConfig` exist for consumers but are **not covered by any test**:
`mockMode(true)` and the injectable `httpClient(...)`. Nothing currently fails if either
regresses. Worth a test before either is relied on.

## VibeTags in the build

The [VibeTags](https://github.com/PIsberg/vibetags) annotation processor runs on every compile
and rewrites the managed region of `CLAUDE.md` plus the per-class files in `.claude/rules/`.
Its annotations are `RetentionPolicy.SOURCE`, so nothing reaches the jar.

Gradle needs `-Avibetags.root=${rootDir}` explicitly. Without it the processor resolves its
output root from the JVM working directory, which under Gradle is the daemon's directory, and
the guardrails are written somewhere under `~/.gradle` instead of into the repo.

Generated build state (`.vibetags-mod-*`, `.vibetags-cache`, `vibetags.log`) is gitignored.
The generated guardrails themselves are committed, so a reviewer sees them change.
