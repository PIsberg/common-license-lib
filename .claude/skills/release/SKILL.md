---
name: release
description: Cuts a common-license-lib release — bumps all six version locations, writes the CHANGELOG entry, verifies both builds locally, opens the PR, and stops before merge or tag. Use when asked to release, cut, or publish a new version of this library.
---

# Releasing common-license-lib

Full background lives in [docs/releasing.md](../../../docs/releasing.md),
[docs/build-and-test.md](../../../docs/build-and-test.md) and
[docs/MAVEN_CENTRAL_SETUP.md](../../../docs/MAVEN_CENTRAL_SETUP.md). This skill is the
step-by-step checklist for running that process without missing one of the six version copies —
the thing that has already caused one out-of-band fix commit
(`chore(release): sync the four version pins 0.3.0 -> 0.4.0 missed in #48`).

Stop at the PR. Merging, tagging and publishing are irreversible (a published Maven Central
coordinate can never be replaced or withdrawn) and are the user's call, not this skill's.

## 0. Figure out the version and the changelog content

1. Confirm the target version number with the user if it wasn't given explicitly (semver: a new
   public class/method is at least a minor bump, a behavior-only fix is a patch bump).
2. `git log v<previous>..HEAD --oneline` and `git diff v<previous>..HEAD --stat` to see what
   actually shipped since the last tag.
3. Check `CHANGELOG.md`'s `## [Unreleased]` section — if entries already accumulated there,
   fold them in rather than re-deriving from commits; otherwise write the entry from the commit
   log and PR bodies (`gh pr view <n> --json title,body`), in the style of the existing entries:
   a heading, one prose paragraph on the *why*, a bullet list of what was added, then any
   behavior worth calling out explicitly (each one pinned by a test — check the actual test file
   before claiming that).

## 1. Branch

Never edit on `main`. Branch from an up-to-date `main`:

```bash
git checkout main && git pull
git checkout -b release/<version>
```

## 2. Bump all six version locations, in one commit

Nothing in CI compares these — check all six by hand.

| File | Field(s) |
| :--- | :--- |
| `pom.xml` | `<version>` |
| `gradle.properties` | `version=` |
| `consumer-fixture/pom.xml` | `<version>` *and* `<common-license-lib.version>` |
| `examples/minimal-gate/pom.xml` | `<version>` *and* `<common-license-lib.version>` |
| `README.md` | the Maven `<version>` snippet *and* the Gradle `implementation(...)` snippet |
| `CHANGELOG.md` | insert `## [<version>] - <YYYY-MM-DD>` after `## [Unreleased]` |

After editing, confirm nothing was missed:

```bash
grep -rn "<old-version>" pom.xml gradle.properties consumer-fixture/pom.xml examples/minimal-gate/pom.xml README.md
```

An empty result (grep exit 1) is success.

## 3. Verify both builds locally before pushing

Don't rely on CI to catch a version-bump typo — it's cheap to check first and CI can take a
while (or, per §5, can be down):

```bash
mvn clean verify        # exit 0 required
./gradlew build         # exit 0 required
```

Both must pass. If either fails, fix it before committing — a release commit that doesn't build
is worse than not releasing.

## 4. Commit, push, open the PR

```bash
git add pom.xml gradle.properties consumer-fixture/pom.xml examples/minimal-gate/pom.xml README.md CHANGELOG.md
git commit -m "chore(release): <version>"
git push -u origin release/<version>
gh pr create --title "chore(release): <version>" --body "..."
```

The PR body should state what was verified locally (both builds green, N version locations
confirmed) and spell out the next steps are deliberately not part of this PR: merge after CI is
green, then tag and push the tag to publish.

**Do not merge the PR.** Hand it back once it's open and CI is green.

## 5. Watch CI — and know the difference between "red" and "not running"

`tests.yml` builds Maven on Java 21 and 25, Gradle on Java 21, and runs the consumer fixture and
the examples. Poll with:

```bash
gh pr checks <pr-number>
```

If checks stay completely absent for several minutes — not failing, just never appearing (
`gh api repos/<owner>/<repo>/commits/<sha>/check-suites` returns `"total_count": 0`) — that is
not a problem with the PR. Check `https://www.githubstatus.com/api/v2/summary.json` for an
active Actions incident before assuming anything is broken in the branch, and tell the user what
you found rather than silently retrying (empty-commit pushes, closing/reopening the PR) forever.

A skipped or never-run gate is not a passed gate — report the actual state, including "CI hasn't
run yet because GitHub Actions is down," rather than treating silence as green.

## 6. After the user merges

Only after the PR is merged, and only with the user's go-ahead:

```bash
git checkout main && git pull
git tag v<version>
git push origin v<version>
```

Pushing the tag is the trigger — `publish.yml` deploys to Maven Central and cannot be undone.
Confirm with the user before running the `git push origin v<version>` step even if they already
approved the release in general; tagging is the one command in this whole flow that is not
reversible.
