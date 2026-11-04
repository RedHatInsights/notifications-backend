#!/usr/bin/env bash
# fullsend-release-watch-poll.sh: decide whether the fullsend-release-watch
# agent has an upgrade to analyze, make sure it has a tracking issue in this
# repo, and write the dispatch matrix for reusable-dispatch.yml to
# $GITHUB_OUTPUT.
#
# Called by .github/workflows/fullsend-release-watch.yml.
#
# What is analyzed: the upgrade from the fullsend version this repository
# runs to the latest published fullsend-ai/fullsend release. The version this
# repository runs is the OLDEST fullsend workflow pin in .github/workflows/,
# that is any `uses: fullsend-ai/fullsend/.github/workflows/<file>@<sha> #
# vX.Y.Z` (reusable-dispatch.yml, reusable-prioritize.yml, ...), read from the
# checkout in WORKFLOWS_DIR, because every workflow still on it has to go
# through the whole upgrade. The agent's
# pre-script collects the release notes of every release in that range.
#
# State lives in the tracking issues, so no repository variable is needed:
#   - There is one issue per target release, labelled fullsend-release-watch,
#     whose body starts with
#     "<!-- fullsend-release-watch:tag=<to> from=<from> -->".
#   - The agent's post-script records the outcome in a sticky bot comment
#     that starts with "<!-- fullsend-release-watch:status=<status> -->".
#   - The target is done when its issue is closed, or when that comment says
#     ok or findings. A missing comment or status=error means it is retried
#     on the next run.
#   - When a newer release becomes the target, the previous open tracking
#     issues are closed as superseded: the new range includes their releases.
#
# Required env:
#   GH_TOKEN      token with issues:write on REPO (the workflow's GITHUB_TOKEN)
#   REPO          owner/repo of this repository
#   GITHUB_OUTPUT set by GitHub Actions
# Optional env:
#   FORCE         "true" re-analyzes the latest release even if it is done
#   WORKFLOWS_DIR directory holding this repo's workflows (default
#                 .github/workflows)
#
# Issues are created with the workflow's GITHUB_TOKEN on purpose: events
# caused by GITHUB_TOKEN do not start other workflows, so creating or
# closing a tracking issue never triggers the fullsend.yaml triage shim.

set -euo pipefail

: "${GH_TOKEN:?GH_TOKEN must be set}"
: "${REPO:?REPO must be set}"
: "${GITHUB_OUTPUT:?GITHUB_OUTPUT must be set}"
FORCE="${FORCE:-false}"
WORKFLOWS_DIR="${WORKFLOWS_DIR:-.github/workflows}"

UPSTREAM="fullsend-ai/fullsend"
AGENT="fullsend-release-watch"
LABEL="fullsend-release-watch"
# Must match MARKER in .fullsend/scripts/post-fullsend-release-watch.sh.
RESULT_MARKER="<!-- fullsend:fullsend-release-watch-agent -->"
export LC_ALL="${LC_ALL:-C.UTF-8}"

# jq helpers: semver tags only (vX.Y.Z, so release candidates are excluded),
# compared numerically.
JQ_SEMVER='def semver: test("^v[0-9]+\\.[0-9]+\\.[0-9]+$");
           def ver: ltrimstr("v") | split(".") | map(tonumber);'

finish() {
  local matrix="$1" has_work="$2" delim
  delim="MATRIX_$(openssl rand -hex 8)"
  {
    echo "matrix<<${delim}"
    printf '%s\n' "${matrix}"
    echo "${delim}"
    echo "has_work=${has_work}"
  } >> "${GITHUB_OUTPUT}"
}

# 1. The version this repository runs: the oldest fullsend workflow pin.
# Only an exact release pin counts: a 40-character SHA followed by a
# "# vX.Y.Z" comment that ends the line. Any other fullsend workflow
# reference (a branch, a release candidate) is reported and ignored.
# Both patterns are anchored to a YAML `uses:` key, so text that merely
# mentions the workflow (a grep in a run: script) is not mistaken for a pin.
uses_re="^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]*${UPSTREAM}/\.github/workflows/[A-Za-z0-9._-]+\.ya?ml@"
pin_re="${uses_re}[0-9a-f]{40}[[:space:]]+#[[:space:]]*v[0-9]+\.[0-9]+\.[0-9]+[[:space:]]*$"
refs=$(grep -hE "${uses_re}" \
  "${WORKFLOWS_DIR}"/*.yml "${WORKFLOWS_DIR}"/*.yaml 2>/dev/null || true)
pins=$(grep -E "${pin_re}" <<< "${refs}" | sed -E 's/.*#[[:space:]]*(v[0-9.]+)[[:space:]]*$/\1/' || true)
while IFS= read -r other; do
  [[ -n "${other}" ]] || continue
  printf '::warning::ignoring a fullsend workflow reference that is not a release pin: %s\n' "$(sed -E 's/^[[:space:]]+//' <<< "${other}")"
done < <(grep -vE "${pin_re}" <<< "${refs}" || true)
if [[ -z "${pins}" ]]; then
  echo "::error::no '${UPSTREAM}/.github/workflows/<file>@<sha> # vX.Y.Z' pin found in ${WORKFLOWS_DIR}" >&2
  exit 1
fi
from=$(jq -rRs "${JQ_SEMVER}"' split("\n") | map(select(semver)) | sort_by(ver) | first' <<< "${pins}")

# 2. The latest published release (no drafts or pre-releases).
to=$(gh api "repos/${UPSTREAM}/releases?per_page=30" \
  | jq -r "${JQ_SEMVER}"' [.[] | select(.draft == false and .prerelease == false) | .tag_name | select(semver)]
          | sort_by(ver) | last // empty')
if [[ -z "${to}" ]]; then
  echo "::error::no published ${UPSTREAM} release found" >&2
  exit 1
fi

if [[ "$(jq -rn --arg a "${from}" --arg b "${to}" "${JQ_SEMVER}"' ($a | ver) < ($b | ver)')" != "true" ]]; then
  echo "This repository runs fullsend ${from}; the latest release is ${to}. Nothing to analyze."
  finish '{"include":[]}' false
  exit 0
fi
echo "This repository runs fullsend ${from}; the latest release is ${to}."

# The label must exist before issues can be listed or created with it.
# "already exists" is the expected failure; anything else surfaces when the
# list or create call below fails.
gh label create "${LABEL}" --repo "${REPO}" --color "5319E7" \
  --description "fullsend upgrade impact analysis" >/dev/null 2>&1 || true

# 3. Tracking issues, keyed by the target tag in the first line of their
# body. Only issues the workflow itself created count, so a look-alike issue
# opened by someone else cannot mark a release as done.
tracked=$(gh issue list --repo "${REPO}" --label "${LABEL}" --state all \
    --limit 500 --json number,url,state,body,author \
  | jq -c '[.[]
            | select(.author.login == "app/github-actions" or .author.login == "github-actions")
            | (.body // "" | capture("^<!-- fullsend-release-watch:tag=(?<tag>v[0-9]+\\.[0-9]+\\.[0-9]+) from=(?<from>v[0-9]+\\.[0-9]+\\.[0-9]+) -->")?) as $m
            | {number, url, state, tag: $m.tag}]')

# Prints ok, findings, error, or nothing for an issue's latest result
# comment written by a bot (the agent's post-script).
result_status() {
  gh api --paginate "repos/${REPO}/issues/$1/comments?per_page=100" \
    | jq -rs --arg marker "${RESULT_MARKER}" '
        add // []
        | map(select(.user.type == "Bot" and ((.body // "") | contains($marker))))
        | last
        | if . == null then empty
          else ((.body | capture("<!-- fullsend-release-watch:status=(?<s>ok|findings|error) -->") | .s)? // empty)
          end'
}

issue=$(jq -c --arg tag "${to}" 'map(select(.tag == $tag)) | sort_by(.number) | first // empty' <<< "${tracked}")

if [[ -z "${issue}" ]]; then
  # 4a. First time this release is the target: open its tracking issue.
  # The first line is the hidden marker the lookup above reads; it must stay
  # the first line.
  file=$(mktemp)
  # shellcheck disable=SC2016
  {
    printf '<!-- fullsend-release-watch:tag=%s from=%s -->\n' "${to}" "${from}"
    printf '> [!CAUTION]\n'
    printf '> **Do not edit this description.** It is managed by `.github/workflows/fullsend-release-watch.yml`: editing it breaks release tracking and starts the triage agent on this issue. Comment instead.\n\n'
    printf 'Impact of upgrading fullsend from `%s` to [`%s`](https://github.com/%s/releases/tag/%s) ([compare](https://github.com/%s/compare/%s...%s)). The analysis is posted as a comment below.\n' \
      "${from}" "${to}" "${UPSTREAM}" "${to}" "${UPSTREAM}" "${from}" "${to}"
  } > "${file}"
  url=$(gh issue create --repo "${REPO}" \
    --title "fullsend ${from} → ${to}: upgrade impact analysis" \
    --label "${LABEL}" --body-file "${file}")
  rm -f "${file}"
  number="${url##*/}"
  if [[ ! "${number}" =~ ^[0-9]+$ ]]; then
    echo "::error::could not create a tracking issue for ${to} (gh printed: ${url})" >&2
    exit 1
  fi
  echo "Created tracking issue #${number} for ${to}."

  # The new range includes every release an older open issue covered.
  while IFS= read -r old; do
    [[ -n "${old}" ]] || continue
    if gh issue close "${old}" --repo "${REPO}" --reason "not planned" \
        --comment "Superseded by #${number}, which analyzes the upgrade from ${from} to ${to}, including this issue's releases."; then
      echo "Closed superseded tracking issue #${old}."
    else
      echo "::warning::could not close superseded tracking issue #${old}"
    fi
  done < <(jq -r --arg tag "${to}" '.[] | select(.state == "OPEN" and .tag != $tag) | .number' <<< "${tracked}")
else
  number=$(jq -r '.number' <<< "${issue}")
  url=$(jq -r '.url' <<< "${issue}")
  if [[ "${FORCE}" == "true" ]]; then
    echo "${to}: re-dispatching for tracking issue #${number} (force)."
  else
    if [[ "$(jq -r '.state' <<< "${issue}")" == "CLOSED" ]]; then
      echo "${to}: tracking issue #${number} is closed; nothing to do."
      finish '{"include":[]}' false
      exit 0
    fi
    status=$(result_status "${number}")
    if [[ "${status}" == "ok" || "${status}" == "findings" ]]; then
      echo "${to}: already analyzed (status=${status}, #${number}); nothing to do."
      finish '{"include":[]}' false
      exit 0
    fi
    echo "${to}: dispatching for tracking issue #${number}."
  fi
fi

# 5. One matrix entry. The pre-script reads release_watch.from/to from this
# payload and collects the release notes for that range.
matrix=$(jq -nc \
  --arg agent "${AGENT}" \
  --arg repo "${REPO}" \
  --arg number "${number}" \
  --arg url "${url}" \
  --arg from "${from}" \
  --arg to "${to}" \
  '{include: [{
      agent: $agent,
      source_repo: $repo,
      role: "triage",
      event_payload: ({issue: {number: ($number | tonumber), html_url: $url},
                       release_watch: {from: $from, to: $to}} | tojson),
      status_repo: $repo,
      status_number: $number
    }]}')
finish "${matrix}" true
