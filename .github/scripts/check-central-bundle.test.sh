#!/usr/bin/env bash
#
# Cases for check-central-bundle.sh. Each builds a bundle zip with the JDK's jar tool and asserts
# the checker's exit code: 0 accepted, 1 rejected, 2 usage or unreadable bundle.
#
# The rejected layouts are the ones Central has actually refused or would refuse, chief among them
# the files Maven 3.10's resolver leaves in central-publishing-maven-plugin's staging directory
# (maven-metadata-local.xml at the artifact level, _remote.repositories beside the artifacts),
# which is what sank the first two v0.6.0 publish runs.
#
# Usage: check-central-bundle.test.sh   (from anywhere; exits non-zero if any case fails)
set -uo pipefail

check="$(cd "$(dirname "$0")" && pwd)/check-central-bundle.sh"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

G=se.deversity.common A=common-license-lib V=9.9.9
dir="se/deversity/common/$A/$V"
stem="$A-$V"

# A complete, valid component: pom, main/sources/javadoc jars, cyclonedx SBOM, each with checksums
# and a signature, the way the publish job stages it.
valid_tree() {
  local root="$1" f sum
  mkdir -p "$root/$dir"
  for f in "$stem.pom" "$stem.jar" "$stem-sources.jar" "$stem-javadoc.jar" "$stem-cyclonedx.xml"; do
    echo "$f" > "$root/$dir/$f"
    echo sig > "$root/$dir/$f.asc"
    for sum in md5 sha1 sha256 sha512; do
      echo "$sum" > "$root/$dir/$f.$sum"
      echo "$sum" > "$root/$dir/$f.asc.$sum"
    done
  done
}

# bundle <name> <mutation command run inside the tree>
bundle() {
  local name="$1" mutate="$2" root="$work/$1"
  valid_tree "$root"
  (cd "$root" && eval "$mutate")
  (cd "$root" && jar cMf "$work/$name.zip" se)
  printf '%s\n' "$work/$name.zip"
}

failed=0
expect() {
  local want="$1" label="$2"
  shift 2
  "$@" > "$work/out.txt" 2>&1
  local got=$?
  if [ "$got" -eq "$want" ]; then
    echo "ok   $label (exit $got)"
  else
    echo "FAIL $label: expected exit $want, got $got"
    sed 's/^/     /' "$work/out.txt"
    failed=1
  fi
}

expect 0 "a complete component is accepted" \
  bash "$check" "$(bundle valid ':')" "$G" "$A" "$V"
expect 1 "maven-metadata-local.xml at the artifact level (Maven 3.10) is rejected" \
  bash "$check" "$(bundle metadata "echo x > se/deversity/common/$A/maven-metadata-local.xml")" "$G" "$A" "$V"
expect 1 "_remote.repositories beside the artifacts (Maven 3.10) is rejected" \
  bash "$check" "$(bundle remote "echo x > $dir/_remote.repositories")" "$G" "$A" "$V"
expect 1 "a subdirectory of the version directory (.locks/ on Windows) is rejected" \
  bash "$check" "$(bundle locks "mkdir -p $dir/.locks && echo x > $dir/.locks/x.lock")" "$G" "$A" "$V"
expect 1 "a missing .pom is rejected" \
  bash "$check" "$(bundle nopom "rm $dir/$stem.pom")" "$G" "$A" "$V"
expect 1 "a missing -javadoc jar is rejected" \
  bash "$check" "$(bundle nojavadoc "rm $dir/$stem-javadoc.jar")" "$G" "$A" "$V"
expect 1 "a jar without its .sha1 is rejected" \
  bash "$check" "$(bundle nosha1 "rm $dir/$stem.jar.sha1")" "$G" "$A" "$V"
expect 1 "a bundle for another version is rejected" \
  bash "$check" "$(bundle valid2 ':')" "$G" "$A" 9.9.8
expect 2 "too few arguments is a usage error" \
  bash "$check" "$work/valid.zip" "$G" "$A"
expect 2 "a missing bundle file is an error, not a pass" \
  bash "$check" "$work/does-not-exist.zip" "$G" "$A" "$V"

exit "$failed"
