#!/usr/bin/env bash
#
# Build the Maven Central bundle publish.yml would upload, without publishing anything, and check
# it with check-central-bundle.sh.
#
# Only Central ever validated a bundle, and only after the release tag was pushed. That is how the
# v0.6.0 release learned on 2026-10-10 that the runner image's Maven 3.10 makes
# central-publishing-maven-plugin 0.11.0 zip files Central rejects. This builds the same bundle on
# every pull request, so the next change of that kind fails a check instead of a release.
#
# The plugin is pointed at the discard port on localhost with throwaway credentials, so it builds,
# stages and zips the real bundle and then fails to upload it. That failure is the expected end of
# the build; what decides the result is the bundle it left behind.
#
# Usage: build-central-bundle.sh                      (from anywhere in the repository)
#   MVN=<path>          the Maven to run, default `mvn` on PATH
#   MAVEN_VERSION=<v>   if set, refuse to run unless that Maven is version <v>
set -uo pipefail

MVN="${MVN:-mvn}"
root="$(cd "$(dirname "$0")/../.." && pwd)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# Credentials for the server id the plugin's publishingServerId names, so it gets as far as the
# upload. Also passed as the global settings: the build needs none of the installation's.
cat > "$work/settings.xml" <<'EOF'
<settings>
  <servers>
    <server><id>central</id><username>bundle-check</username><password>not-a-token</password></server>
  </servers>
</settings>
EOF

if ! mvn_version="$("$MVN" --version)"; then
  echo "::error::$MVN --version failed"
  exit 1
fi
printf '%s\n' "$mvn_version"
if [ -n "${MAVEN_VERSION:-}" ] && ! printf '%s\n' "$mvn_version" | grep -qF "Apache Maven $MAVEN_VERSION "; then
  echo "::error::this is not Maven $MAVEN_VERSION, the version publish.yml deploys with, so its bundle says nothing about a release"
  exit 1
fi

# The first element of each name in pom.xml is the project's own: the pom has no parent.
first() { sed -n "/^ *<$1>/{s|^ *<$1>\([^<]*\)</$1>.*|\1|p;q;}" "$root/pom.xml"; }
group="$(first groupId)" artifact="$(first artifactId)" version="$(first version)"

bundle="$root/target/central-publishing/central-bundle.zip"
log="$work/deploy.log"
rm -f "$bundle"   # a bundle left by an earlier build must not be the one checked

echo "::group::mvn clean deploy, uploading to http://127.0.0.1:9"
(cd "$root" && "$MVN" -B clean deploy -DskipTests -DcentralBaseUrl=http://127.0.0.1:9 \
    -s "$work/settings.xml" -gs "$work/settings.xml") > "$log" 2>&1
status=$?
tail -n 25 "$log"
echo "::endgroup::"

if [ "$status" -eq 0 ]; then
  echo "::error::the deploy reported success while pointed at http://127.0.0.1:9; read its log before trusting anything it did"
  exit 1
elif [ ! -f "$bundle" ]; then
  echo "::error::the build failed before the plugin wrote a bundle"
  tail -n 80 "$log"
  exit 1
elif ! grep -qF "Using Central baseUrl: http://127.0.0.1:9" "$log"; then
  echo "::error::the publishing plugin did not take the local base URL"
  exit 1
fi
bash "$root/.github/scripts/check-central-bundle.sh" "$bundle" "$group" "$artifact" "$version"
