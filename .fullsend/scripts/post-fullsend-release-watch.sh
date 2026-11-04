#!/usr/bin/env bash
set -euo pipefail

# Prefer the validated iteration directory set by the harness's
# validation_loop -- without it, scanning for the last iteration could pick
# up output that failed validation.
if [[ -n "${FULLSEND_VALIDATED_ITERATION_DIR:-}" ]]; then
  RESULT_FILE="${FULLSEND_VALIDATED_ITERATION_DIR}/agent-result.json"
else
  RESULT_FILE=""
  for dir in iteration-*/output; do
    if [[ -f "${dir}/agent-result.json" ]]; then
      RESULT_FILE="${dir}/agent-result.json"
    fi
  done
fi

if [[ -z "${RESULT_FILE}" ]] || [[ ! -f "${RESULT_FILE}" ]]; then
  echo "ERROR: agent-result.json not found"
  exit 1
fi

# The agent runs in an untrusted sandbox -- treat its output as untrusted
# input: validate JSON structure and shape before acting on it. `jq empty`
# only rejects syntax errors -- a syntactically valid non-object (e.g. a
# bare array) would pass it and then blow up on the first `.field` access
# below, so the object-type check is required too.
if ! jq empty "${RESULT_FILE}" 2>/dev/null; then
  echo "ERROR: agent-result.json is not valid JSON"
  exit 1
fi

if [[ "$(jq -r 'type' "${RESULT_FILE}" 2>/dev/null)" != "object" ]]; then
  echo "ERROR: agent-result.json is not a JSON object"
  exit 1
fi

STATUS=$(jq -r '.status // ""' "${RESULT_FILE}")
SUMMARY=$(jq -r '.summary // ""' "${RESULT_FILE}")
BODY=$(jq -r '.body // ""' "${RESULT_FILE}")
# reusable-dispatch.yml writes the payload under the workspace, which is this
# repo's checkout; `fullsend run` starts this script in its run directory, so
# a relative path does not work.
PAYLOAD="${GITHUB_WORKSPACE:?}/.fullsend/dispatch/event-payload.json"
# The markers of the analysis section, shared with the upgrade workflow's scripts.
# shellcheck source-path=SCRIPTDIR source=../../.github/scripts/fullsend-release-watch/markers.sh
source "${GITHUB_WORKSPACE}/.github/scripts/fullsend-release-watch/markers.sh"
if [[ ! -f "${PAYLOAD}" ]]; then
  echo "ERROR: event payload not found at ${PAYLOAD}"
  exit 1
fi
TAG=$(jq -r '.tag // "unknown"' "${PAYLOAD}")
FROM_REF=$(jq -r '.from_ref // ""' "${PAYLOAD}")
PR_NUMBER=$(jq -r '.pr_number // ""' "${PAYLOAD}")

if ! [[ "${PR_NUMBER}" =~ ^[0-9]+$ ]]; then
  echo "ERROR: event payload carries no valid pr_number"
  exit 1
fi

# Model output comes from untrusted release text. Break @mentions so the PR
# description cannot notify users, and closing keywords followed by an issue
# reference ("Fixes #12", "closes: owner/repo#3", "resolves https://...") so
# merging the PR cannot close issues. A zero-width space goes after the @ and
# after the first letter of the keyword.
# GitHub makes no mention when an ASCII letter, a digit or _ comes before the
# @, so emails, image digests and `uses: x@ref` stay intact. The loop
# (:a ... ta) re-checks each @ from the start, because a match consumes the
# character after the @, which can be the character before the next @.
# The entities &#64; &#x40; &commat; also make a mention, so their & is
# escaped and they show as plain text.
neutralize_references() {
  local ref='(:?[[:space:]]+(([A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+)?#[0-9]|https?://))'
  sed -E \
    -e 's/&(#0*64;|#[xX]0*40;|commat;)/\&amp;\1/g' \
    -e ':a' -e 's/(^|[^A-Za-z0-9_])@([A-Za-z0-9_-])/\1@\xe2\x80\x8b\2/' -e 'ta' \
    -e "s~\<(c)(lose[sd]?${ref})~\1\xe2\x80\x8b\2~gI" \
    -e "s~\<(f)(ix(e[sd])?${ref})~\1\xe2\x80\x8b\2~gI" \
    -e "s~\<(r)(esolve[sd]?${ref})~\1\xe2\x80\x8b\2~gI"
}

case "${STATUS}" in
  ok)
    summary_shown="${SUMMARY//[$'\r\n']/ }"
    printf '%s\n' "fullsend ${TAG}: no impact (${summary_shown})"
    ANALYSIS="Nothing in the release notes needs a decision beyond this pull request. ${SUMMARY}"
    ;;
  findings)
    ANALYSIS="${BODY}"
    ;;
  error)
    # The upgrade can cross several releases, so name the whole range.
    if [[ -n "${FROM_REF}" ]]; then
      notes="the notes of every release after \`${FROM_REF}\` up to \`${TAG}\`: https://github.com/fullsend-ai/fullsend/releases"
    else
      notes="the release notes: https://github.com/fullsend-ai/fullsend/releases/tag/${TAG}"
    fi
    ANALYSIS="**The automated release analysis failed** (${SUMMARY}). Read ${notes}"
    # The prompt asks for what stopped the analysis, then any partial findings.
    if [[ -n "${BODY}" ]]; then
      ANALYSIS+=$'\n\n'"What the analysis reported before it failed:"$'\n\n'"${BODY}"
    fi
    ;;
  *)
    shown="${STATUS//[$'\r\n']/ }"
    printf '%s\n' "ERROR: Unknown or missing status '${shown:0:40}'"
    exit 1
    ;;
esac

START="${ANALYSIS_START}" END="${ANALYSIS_END}"

ANALYSIS=$(neutralize_references <<< "${ANALYSIS}")

# pr_number 0 is an agent-only test run: print the result instead of editing a
# PR. The runner trims leading whitespace before it parses workflow commands,
# so indenting is not enough: stop command processing with a random token
# around the model output.
if [[ "${PR_NUMBER}" == "0" ]]; then
  echo "Agent-only run (status: ${STATUS//[$'\r\n']/ }). Analysis:"
  stop_token="$(openssl rand -hex 16)"
  echo "::stop-commands::${stop_token}"
  printf '%s\n' "${ANALYSIS}"
  echo "::${stop_token}::"
  exit 0
fi
current=$(gh pr view "${PR_NUMBER}" --repo "${REPO_FULL_NAME}" --json body --jq '.body')
if [[ "${current}" == *"${START}"* && "${current}" == *"${END}"* ]]; then
  prefix="${current%%"${START}"*}"
  suffix="${current#*"${END}"}"
  new_body="${prefix}${START}"$'\n\n'"${ANALYSIS}"$'\n\n'"${END}${suffix}"
else
  new_body="${current}"$'\n\n'"${ANALYSIS_HEADING}"$'\n\n'"${START}"$'\n\n'"${ANALYSIS}"$'\n\n'"${END}"
fi

body_file=$(mktemp)
printf '%s\n' "${new_body}" > "${body_file}"
if ! gh pr edit "${PR_NUMBER}" --repo "${REPO_FULL_NAME}" --body-file "${body_file}"; then
  echo "WARN: could not edit the PR description; posting a comment instead"
  printf '%s\n' "${ANALYSIS}" | gh pr comment "${PR_NUMBER}" --repo "${REPO_FULL_NAME}" --body-file -
fi
