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
   on Java 21, and runs the consumer fixture and the examples. Green has to include
   `Central Bundle Shape`: it is the only check that builds the bundle Central will validate,
   and Central sees the real one only after the tag exists.
4. Merge.
5. Tag the merge commit and push the tag: `git tag v0.2.2 && git push origin v0.2.2`.

Pushing a `v*` tag is the trigger. `publish.yml` then deploys to Maven Central with
`mvn --batch-mode clean deploy -P release`, signs the jar, sources and javadoc with keyless
cosign, and opens a GitHub Release carrying the artifacts and their `.bundle` signatures.

A published Maven Central version cannot be replaced or withdrawn, and the tag is what
publishes. Wait for green before pushing it.

## The pinned Maven

`publish.yml` does not deploy with the runner image's Maven. It downloads the version in its
`MAVEN_VERSION` env from Maven Central, checks it against `MAVEN_SHA512`, and refuses to deploy if
`mvn --version` reports anything else. The ubuntu-24.04 image moved to Maven 3.10 in its 20261004
release, and `central-publishing-maven-plugin` 0.11.0 under 3.10 zips the resolver's
`maven-metadata-local.xml` and `_remote.repositories` into the bundle. Central rejected v0.6.0
twice for it: "Bundle has content that does NOT have a .pom file". Stay on 3.9 until a plugin
release handles 3.10.

To move the pin, change `MAVEN_VERSION` and `MAVEN_SHA512` in `publish.yml`, nowhere else:
`Central Bundle Shape` in `tests.yml` reads both from there and fails if its install step stops
matching. Take the SHA-512 from
`https://archive.apache.org/dist/maven/maven-3/<v>/binaries/apache-maven-<v>-bin.tar.gz.sha512`
and compare it with `sha512sum` of the archive from Maven Central, which is where the workflow
fetches it. The PR's `Central Bundle Shape` run is what verifies a bump of the pin or of the plugin.

The same check runs locally, given a Maven to test:

```bash
MVN=/path/to/apache-maven-3.9.16/bin/mvn MAVEN_VERSION=3.9.16 bash .github/scripts/build-central-bundle.sh
```

Run it on Linux or WSL. On Windows the plugin writes `\` into the zip's entry names, so every
bundle built there fails the check whatever the Maven version.

## When the publish fails

A failed Central validation publishes nothing and leaves the version free, so the release is
finished from the same tag, never re-cut. Confirm nothing went out first: `curl -s -o /dev/null -w
'%{http_code}' https://repo1.maven.org/maven2/se/deversity/common/common-license-lib/<v>/common-license-lib-<v>.pom`
must answer 404. Then fix the cause on `main` through a PR and dispatch the workflow from `main`
against the existing tag:

```bash
gh workflow run publish.yml --ref main -f tag=v<version>
```

A dispatch runs `publish.yml` as it is on `main` and builds the source of the tag, so a workflow
fix reaches a tag that predates it. It refuses a tag whose `pom.xml` version differs from the tag
name. Like the tag push, a dispatch that succeeds cannot be undone.

## The two publish paths

`publish.yml` uses Maven. `build.gradle.kts` also configures
`com.vanniktech.maven.publish` against the Central Portal, which is the path used for a local
`./gradlew publishToMavenLocal` or a manual publish; it signs only when the
`signingInMemoryKey` Gradle property is present.

Because both paths exist, the POM metadata is maintained twice. The name, description, licence,
developer, SCM and issue-management blocks in `pom.xml` and in the `mavenPublishing { pom { … } }`
block of `build.gradle.kts` have to say the same thing, and no test checks that they do.
