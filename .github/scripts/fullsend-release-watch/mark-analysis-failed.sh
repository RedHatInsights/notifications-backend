#!/usr/bin/env bash
# Replaces the "analysis is running" placeholder in the upgrade PR description
# when the agent did not. Does nothing if the agent already replaced it.
#
# Env: GH_TOKEN (pull-requests: write), REPO, PR_NUMBER, RUN_URL.
set -euo pipefail
# shellcheck source-path=SCRIPTDIR source=markers.sh
source "$(dirname "${BASH_SOURCE[0]}")/markers.sh"

: "${GH_TOKEN:?}" "${REPO:?}" "${PR_NUMBER:?}" "${RUN_URL:?}"

START="${ANALYSIS_START}" END="${ANALYSIS_END}" PLACEHOLDER="${ANALYSIS_PLACEHOLDER}"

current=$(gh pr view "${PR_NUMBER}" --repo "${REPO}" --json body --jq '.body')
note="**The automated release analysis did not update this section** (${RUN_URL}). If a comment on this pull request has the analysis, use it. Otherwise, read the release notes manually before merging."

# The managed section runs from the last START before the first END to that
# END. Someone may have edited the markers by hand, and the post-script then
# appends a fresh section, so the first START is not always the right one.
head="${current%%"${END}"*}"
before="${head%"${START}"*}"
if [[ "${head}" != "${current}" && "${before}" != "${head}" ]]; then
  section="${head#"${before}${START}"}"
  if [[ "${section}" != *"${PLACEHOLDER}"* ]]; then
    echo "analysis already reported"
    exit 0
  fi
  new_body="${before}${START}"$'\n\n'"${note}"$'\n\n'"${END}${current#*"${END}"}"
elif [[ "${current}" == *"${PLACEHOLDER}"* ]]; then
  # Markers damaged: append a section rather than splice on a missing marker.
  new_body="${current}"$'\n\n'"${ANALYSIS_HEADING}"$'\n\n'"${START}"$'\n\n'"${note}"$'\n\n'"${END}"
else
  # No section and no placeholder: the analysis was reported in a damaged
  # section, or update.sh failed before it wrote the description.
  echo "no analysis placeholder to replace"
  exit 0
fi

body_file=$(mktemp)
printf '%s\n' "${new_body}" > "${body_file}"
gh pr edit "${PR_NUMBER}" --repo "${REPO}" --body-file "${body_file}"
