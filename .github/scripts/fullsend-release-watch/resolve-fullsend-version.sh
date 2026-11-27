#!/usr/bin/env bash
# Prints version=<tag> to GITHUB_OUTPUT, read from the `# vX.Y.Z` comment on
# the harness job's reusable-dispatch.yml@ line, so update.sh only has to bump
# that one line.
#
# Env: GITHUB_OUTPUT.
set -euo pipefail

: "${GITHUB_OUTPUT:?}"

# `|| true` lets the error below run.
line=$(grep -E '^\s*uses: fullsend-ai/fullsend/.github/workflows/reusable-dispatch.yml@' .github/workflows/fullsend-release-watch.yml || true)
version=$(sed -E 's/.*#\s*(\S+)\s*$/\1/' <<< "${line}")
if [[ -z "${version}" || "${version}" == "${line}" ]]; then
  echo "::error::could not resolve fullsend version from the reusable-dispatch.yml@ line" >&2
  exit 1
fi
echo "version=${version}" >> "${GITHUB_OUTPUT}"
