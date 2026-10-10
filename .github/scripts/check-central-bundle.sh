#!/usr/bin/env bash
#
# Check that a Central Portal bundle holds exactly one component, laid out the way Central accepts.
#
# Central validates a bundle only after upload, and publish.yml uploads only after the tag is
# pushed. On 2026-10-10 Central rejected v0.6.0 twice with "Bundle has content that does NOT have a
# .pom file: se/deversity/common/common-license-lib": the runner image's Maven had moved to 3.10,
# whose resolver leaves maven-metadata-local.xml and _remote.repositories in the staging directory,
# and central-publishing-maven-plugin 0.11.0 zips that directory whole. This applies the layout
# rules before anything is uploaded:
#
#   - every file sits directly in <group path>/<artifactId>/<version>/, with '/' separators
#   - every file is named <artifactId>-<version>[-<classifier>].<extension>
#   - the .pom, the main jar, -sources.jar and -javadoc.jar are present
#   - every file that is not a checksum or a signature has its .md5 and .sha1 beside it
#
# Not checked: signatures (the pull-request bundle is unsigned; publish.yml signs) and the pom's
# content (name, licenses, scm, developers), which Central also validates.
#
# Usage: check-central-bundle.sh <bundle.zip> <groupId> <artifactId> <version>
# Exit:  0 the layout is valid, 1 it is not (every problem is listed), 2 usage or unreadable bundle
set -uo pipefail

if [ "$#" -ne 4 ]; then
  echo "usage: $0 <bundle.zip> <groupId> <artifactId> <version>" >&2
  exit 2
fi
bundle="$1" group="$2" artifact="$3" version="$4"
dir="$(printf '%s' "$group" | tr . /)/$artifact/$version"
stem="$artifact-$version"

if [ ! -f "$bundle" ]; then
  echo "no bundle at $bundle" >&2
  exit 2
fi
# The JDK's jar rather than unzip: every machine that builds this project has a JDK.
if ! entries="$(jar tf "$bundle")"; then
  echo "cannot list the entries of $bundle" >&2
  exit 2
fi

problems=()
files=()
while IFS= read -r entry; do
  entry="${entry%$'\r'}"                      # jar on Windows ends its lines with CRLF
  case "$entry" in ''|*/) continue ;; esac   # directory entries carry no content
  case "$entry" in
    "$dir"/*/*) problems+=("$entry: in a subdirectory of $dir/") ;;
    "$dir"/"$stem".*|"$dir"/"$stem"-*) files+=("$entry") ;;
    "$dir"/*) problems+=("$entry: not named $stem[-<classifier>].<extension>") ;;
    *) problems+=("$entry: outside $dir/ (entry names must use '/')") ;;
  esac
done <<< "$entries"

has() {
  local f
  for f in "${files[@]}"; do
    [ "$f" = "$1" ] && return 0
  done
  return 1
}

for f in "$stem.pom" "$stem.jar" "$stem-sources.jar" "$stem-javadoc.jar"; do
  has "$dir/$f" || problems+=("$dir/$f: missing")
done

for f in "${files[@]}"; do
  case "$f" in *.md5|*.sha1|*.sha256|*.sha512|*.asc) continue ;; esac
  for sum in md5 sha1; do
    has "$f.$sum" || problems+=("$f: no .$sum beside it")
  done
done

if [ "${#problems[@]}" -gt 0 ]; then
  echo "FAIL $bundle is not a bundle Central accepts for $group:$artifact:$version:"
  printf '  %s\n' "${problems[@]}"
  exit 1
fi
echo "OK   $bundle: ${#files[@]} files in $dir/"
