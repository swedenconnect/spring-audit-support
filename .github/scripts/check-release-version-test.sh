#!/usr/bin/env bash
#
# Tests for .github/scripts/check-release-version.sh.
#
# Most cases feed the check a pre-computed module list, so they run without Maven. The last cases
# read the versions out of the POMs, first of this repository and then of a copy of its POMs set to
# a release version, so that both outcomes are covered whatever version the repository is at.
#
# Usage:
#     .github/scripts/check-release-version-test.sh
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHECK="${HERE}/check-release-version.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

passed=0
failed=0

# expect <name> <expected exit status> <expected text in the output> <tag> [<module lines>]
expect() {
  local name="$1" expected_status="$2" expected_text="$3" tag="$4" modules="${5-}"
  local modules_file="" output status

  if [ -n "$modules" ]; then
    modules_file="${WORK}/modules.txt"
    printf '%s\n' "$modules" > "$modules_file"
  fi

  output="$(GITHUB_ACTIONS= "${CHECK_UNDER_TEST:-$CHECK}" "$tag" $modules_file 2>&1)"
  status=$?

  if [ "$status" -ne "$expected_status" ]; then
    echo "FAIL ${name}: exit status ${status}, expected ${expected_status}"
    echo "${output}" | sed 's/^/     /'
    failed=$((failed + 1))
    return
  fi
  if [ -n "$expected_text" ] && [[ "$output" != *"$expected_text"* ]]; then
    echo "FAIL ${name}: the output does not mention '${expected_text}'"
    echo "${output}" | sed 's/^/     /'
    failed=$((failed + 1))
    return
  fi
  echo "PASS ${name}"
  passed=$((passed + 1))
}

ALL_MATCH='./audit-support/pom.xml 1.2.3
./autoconfigure/pom.xml 1.2.3
./pom.xml 1.2.3
./starter/pom.xml 1.2.3'

expect "every module matches the tag" 0 "All 4 deployed module(s) are at version 1.2.3" \
  v1.2.3 "$ALL_MATCH"

expect "a snapshot version is rejected" 1 "is the snapshot 1.2.3-SNAPSHOT" \
  v1.2.3 './pom.xml 1.2.3-SNAPSHOT'

expect "a snapshot is rejected even when the tag names the snapshot" 1 "is the snapshot 1.2.3-SNAPSHOT" \
  v1.2.3-SNAPSHOT './pom.xml 1.2.3-SNAPSHOT'

expect "a snapshot in a single module is rejected" 1 "./starter/pom.xml is the snapshot 1.2.3-SNAPSHOT" \
  v1.2.3 './pom.xml 1.2.3
./starter/pom.xml 1.2.3-SNAPSHOT'

expect "a module left behind at an older version is rejected" 1 "is 1.2.2, but the tag v1.2.3 requires 1.2.3" \
  v1.2.3 './pom.xml 1.2.3
./autoconfigure/pom.xml 1.2.2'

expect "the parent at another version than the modules is rejected" 1 "./pom.xml is 1.2.4" \
  v1.2.3 './pom.xml 1.2.4
./audit-support/pom.xml 1.2.3'

expect "every mismatch is reported" 1 "2 module(s) do not match the tag v1.2.3" \
  v1.2.3 './pom.xml 1.2.3-SNAPSHOT
./audit-support/pom.xml 1.2.2
./starter/pom.xml 1.2.3'

expect "a tag that is a prefix of the version is rejected" 1 "but the tag v1.2.3 requires 1.2.3" \
  v1.2.3 './pom.xml 1.2.30'

expect "a version that is a prefix of the tag is rejected" 1 "but the tag v1.2.30 requires 1.2.30" \
  v1.2.30 './pom.xml 1.2.3'

expect "a tag without the v prefix is rejected" 1 "does not start with 'v'" \
  1.2.3 './pom.xml 1.2.3'

expect "a tag of nothing but the prefix is rejected" 1 "carries no version" \
  v './pom.xml 1.2.3'

expect "a version with a qualifier is accepted when the tag carries it too" 0 "All 1 deployed module(s)" \
  v1.2.3-RC1 './pom.xml 1.2.3-RC1'

expect "a qualifier missing from the tag is rejected" 1 "but the tag v1.2.3 requires 1.2.3" \
  v1.2.3 './pom.xml 1.2.3-RC1'

expect "nothing to publish is rejected" 1 "No module would be deployed" \
  v1.2.3 ' '

expect "a missing tag is a usage error" 2 "Usage:" ""

echo
echo "Reading the versions out of the POMs of this repository (needs Maven) ..."
POM_VERSION="$(mvn -q --no-transfer-progress -f "${HERE}/../../pom.xml" -Dexpression=project.version -DforceStdout help:evaluate)"
if [ "${POM_VERSION%-SNAPSHOT}" != "$POM_VERSION" ]; then
  expect "this repository, on a snapshot version, is rejected" 1 "is the snapshot ${POM_VERSION}" "v${POM_VERSION%-SNAPSHOT}"
else
  expect "this repository, on the release version, is accepted" 0 "All 4 deployed module(s) are at version ${POM_VERSION}" "v${POM_VERSION}"
  expect "this repository, under a tag for another version, is rejected" 1 "requires 0.0.1" "v0.0.1"
fi

echo
echo "Reading the versions out of a copy of the POMs set to a release version (needs Maven) ..."
COPY="${WORK}/repo"
while IFS= read -r pom; do
  mkdir -p "${COPY}/$(dirname "$pom")"
  cp "${HERE}/../../${pom}" "${COPY}/${pom}"
done < <(cd "${HERE}/../.." && find . -name pom.xml -not -path '*/target/*' -not -path './.git/*')
mkdir -p "${COPY}/.github/scripts"
cp "$CHECK" "${COPY}/.github/scripts/check-release-version.sh"
CHECK_UNDER_TEST="${COPY}/.github/scripts/check-release-version.sh"

# set_version <version> <pom>... replaces the project version in the given POMs.
set_version() {
  local version="$1"
  shift
  for pom in "$@"; do
    sed -i.bak "s|<version>${POM_VERSION}</version>|<version>${version}</version>|g" "$pom" && rm -f "${pom}.bak"
  done
}

set_version 9.8.7 "${COPY}"/pom.xml "${COPY}"/*/pom.xml
expect "a copy on a release version is accepted" 0 "All 4 deployed module(s) are at version 9.8.7" v9.8.7
expect "a copy on a release version, under a tag for another version, is rejected" 1 "requires 9.8.8" v9.8.8

# The starter declares a version of its own, older than that of its parent.
sed -i.bak 's|<artifactId>audit-support-spring-boot-starter</artifactId>|&<version>9.8.6</version>|' \
  "${COPY}/starter/pom.xml" && rm -f "${COPY}/starter/pom.xml.bak"
expect "a copy with a module left behind is rejected" 1 "./starter/pom.xml is 9.8.6, but the tag v9.8.7" v9.8.7

echo "<project>" > "${COPY}/starter/pom.xml"
expect "a copy with a POM that can not be read is rejected" 1 "Could not read the version of" v9.8.7
unset CHECK_UNDER_TEST

echo
echo "${passed} passed, ${failed} failed"
[ "$failed" -eq 0 ]
