# Releasing

Account setup, GPG keys and GitHub secrets are in
[docs/MAVEN_CENTRAL_SETUP.md](MAVEN_CENTRAL_SETUP.md). This page is the release itself.

## The version lives in more than one place

There is no single source of truth for the version, and the copies drift.

| File | Field |
| :--- | :--- |
| `pom.xml` | `<version>` on the project, and the deployed coordinate |
| `gradle.properties` | `version=`, used by the Gradle publish path |
| `consumer-fixture/pom.xml` | its own `<version>` and `common-license-lib.version` |
| `examples/minimal-gate/pom.xml` | its own `<version>` and `common-license-lib.version` |
| `README.md` | the Maven and Gradle install snippets |
| `CHANGELOG.md` | the released section heading |

Nothing in CI compares them. Check all six by hand before tagging.

## Steps

1. Move every version above to the release number, in one commit.
2. Add the `CHANGELOG.md` section for it, and move anything under `[Unreleased]` into it.
3. Open a PR and let CI go green. `tests.yml` builds with Maven on Java 21 and 25, with Gradle
   on Java 21, and runs the consumer fixture and the examples.
4. Merge.
5. Tag the merge commit and push the tag: `git tag v0.2.2 && git push origin v0.2.2`.

Pushing a `v*` tag is the trigger. `publish.yml` then deploys to Maven Central with
`mvn --batch-mode clean deploy -P release`, signs the jar, sources and javadoc with keyless
cosign, and opens a GitHub Release carrying the artifacts and their `.bundle` signatures.

A published Maven Central version cannot be replaced or withdrawn, and the tag is what
publishes. Wait for green before pushing it.

## The two publish paths

`publish.yml` uses Maven. `build.gradle.kts` also configures
`com.vanniktech.maven.publish` against the Central Portal, which is the path used for a local
`./gradlew publishToMavenLocal` or a manual publish; it signs only when the
`signingInMemoryKey` Gradle property is present.

Because both paths exist, the POM metadata is maintained twice. The name, description, licence,
developer, SCM and issue-management blocks in `pom.xml` and in the `mavenPublishing { pom { … } }`
block of `build.gradle.kts` have to say the same thing, and no test checks that they do.
