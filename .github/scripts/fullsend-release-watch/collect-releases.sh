#!/usr/bin/env bash
# Decides whether a fullsend upgrade is due and collects the release notes of
# every release between the repos.yaml pin and the latest stable release, and
# of the fullsend-ai/agents release each one links to.
#
# Env: GH_TOKEN, GITHUB_OUTPUT, REPO. Optional: LAST_SEEN (last tag a PR was
# proposed for), FORCE, AGENT_ONLY, FROM_REF_OVERRIDE (agent-only runs: use
# this release instead of the pin, and ignore the LAST_SEEN and open-PR
# skips), REPOS_YAML, RUNNER_TEMP.
# Outputs: skip, target_tag, from_ref, notes.
set -euo pipefail
# shellcheck source-path=SCRIPTDIR source=markers.sh
source "$(dirname "${BASH_SOURCE[0]}")/markers.sh"

: "${GH_TOKEN:?}" "${GITHUB_OUTPUT:?}" "${REPO:?}"
REPOS_YAML="${REPOS_YAML:-repos.yaml}"
LAST_SEEN="${LAST_SEEN:-}"
FORCE="${FORCE:-false}"
AGENT_ONLY="${AGENT_ONLY:-false}"
FROM_REF_OVERRIDE="${FROM_REF_OVERRIDE:-}"
# The notes pass through a job output and a workflow input, which have size
# limits. The budget is in bytes; see the cap computed below.
MAX_NOTES_BYTES=40000

skip() {
  echo "skip=true" >> "${GITHUB_OUTPUT}"
  echo "Skipping: $1"
  exit 0
}

# True when version $1 is newer than $2.
ver_gt() {
  [[ "$1" != "$2" && "$(printf '%s\n%s\n' "$1" "$2" | sort -V | tail -1)" == "$1" ]]
}

# fullsend_ref sits under the top-level `github:` key.
current=$(awk '/^github:/ {g=1; next} /^[^ #]/ {g=0} g && /^  fullsend_ref:/ {gsub(/["\047]/, "", $2); print $2; exit}' "${REPOS_YAML}")
if [[ -n "${FROM_REF_OVERRIDE}" ]]; then
  # On a real run the override would make the PR describe and analyse a
  # range that does not start at the pin the PR actually upgrades.
  if [[ "${AGENT_ONLY}" != "true" ]]; then
    echo "::error::from_ref is only for agent_only runs" >&2
    exit 1
  fi
  current="${FROM_REF_OVERRIDE}"
  FORCE=true
fi
if [[ -z "${current}" ]]; then
  echo "::error::could not read github.fullsend_ref from ${REPOS_YAML}" >&2
  exit 1
fi

latest=$(gh api repos/fullsend-ai/fullsend/releases/latest --jq '.tag_name')
echo "Pinned: ${current}  Latest: ${latest}"

# ver_gt compares with sort -V, which ranks v1.0.0-rc.1 above v1.0.0 and any
# v-tag above a SHA or a bare 1.0.0, so only stable vX.Y.Z tags are accepted.
for ref in "${current}" "${latest}"; do
  if ! [[ "${ref}" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "::error::${ref} is not a stable fullsend release tag (vX.Y.Z)" >&2
    exit 1
  fi
done

ver_gt "${latest}" "${current}" || skip "fullsend_ref ${current} is not behind the latest release ${latest}"

if [[ "${latest}" == "${LAST_SEEN}" && "${FORCE}" != "true" ]]; then
  skip "an upgrade to ${latest} was already proposed"
fi

# One open bump PR at a time, so a newer release does not stack on an
# unreviewed one. A bump PR without the marker is one a failed run left
# behind after `repos install` opened it: it does not block, so this run can
# complete it.
# Fork PRs are ignored: anyone can open one with a fullsend/bump-* branch and
# the marker, and `repos install` ignores them too.
# --limit: the default 30 can leave an older bump PR out of the list.
bump_prs=$(gh pr list --repo "${REPO}" --state open --limit 1000 --json number,headRefName,isCrossRepository,body \
  --jq '[.[] | select((.isCrossRepository | not) and (.headRefName | startswith("fullsend/bump-")))]')
open_bumps=$(jq -r --arg m "${ANALYSIS_START}" '[.[] | select(.body | contains($m)) | .number] | join(" ")' <<< "${bump_prs}")
incomplete=$(jq -r --arg m "${ANALYSIS_START}" '[.[] | select(.body | contains($m) | not) | .number] | join(" ")' <<< "${bump_prs}")
if [[ -n "${incomplete}" ]]; then
  echo "::warning::incomplete bump PR(s), not counted as open: ${incomplete}"
fi
if [[ -n "${open_bumps}" && "${FORCE}" != "true" ]]; then
  skip "bump PR(s) already open: ${open_bumps}"
fi

# Stable releases after the pin, up to and including the latest. Captured
# first, not read through `< <(...)`: set -e ignores a failing process
# substitution.
releases=$(gh api --paginate 'repos/fullsend-ai/fullsend/releases?per_page=100' \
  --jq '.[] | select(.draft == false and .prerelease == false) | .tag_name' | sort -V)
tags=()
while read -r t; do
  if [[ -n "${t}" ]] && ver_gt "${t}" "${current}" && ! ver_gt "${t}" "${latest}"; then
    tags+=("${t}")
  fi
done <<< "${releases}"
if [[ ${#tags[@]} -eq 0 ]]; then
  echo "::error::no stable release found after ${current} up to ${latest}" >&2
  exit 1
fi

# Bug fixes, refactoring and "others" are most of a changelog and never need a
# decision, so drop them.
filter_body() {
  awk '
    /^#+ / { drop = ($0 ~ /^#+ (Bug Fixes|Refactoring|Others)[[:space:]]*$/) }
    !drop { print }
  '
}

# The agents release notes are GitHub's list of merged pull requests, one
# `* type(scope): title by @user in <PR URL>` line each; `!` marks a breaking
# change, in any type, fix and chore included, so every entry is kept, as
# `* type(scope): title (fullsend-ai/agents#N)`. The lines around the list are
# dropped, and its `## What's Changed` heading, which would read as a release
# section.
filter_agents_body() {
  awk '
    /^#+ / { drop = ($0 ~ /^#+ New Contributors[[:space:]]*$/) }
    /^#+ What.s Changed[[:space:]]*$/ || /^Synced with / || /^\*\*Full Changelog\*\*/ || /^[[:space:]]*$/ { next }
    !drop { print }
  ' | sed -E 's~ by @[^ ]+ in https://github\.com/fullsend-ai/agents/pull/([0-9]+)$~ (fullsend-ai/agents#\1)~'
}

# Since v0.52.0 each fullsend release ends with a link to the fullsend-ai/agents
# release that ships its fleet agents. The upgrade re-pins the harnesses to
# that release, so its notes are collected too. Only a fullsend-ai/agents
# release with a stable tag is followed. The character after the tag is
# matched too, so v1.2.3-rc.1 is not read as v1.2.3.
agents_link='https://github\.com/fullsend-ai/agents/releases/tag/v[0-9]+\.[0-9]+\.[0-9]+([^0-9A-Za-z.-]|$)'
bodies="$(mktemp -d)" agents_dir="$(mktemp -d)"
agents_tags=()
for i in "${!tags[@]}"; do
  raw=$(gh api "repos/fullsend-ai/fullsend/releases/tags/${tags[i]}" --jq '.body // ""')
  filter_body <<< "${raw}" > "${bodies}/${i}"
  agents_tags[i]=""
  link=$(grep -oE "${agents_link}" <<< "${raw}" | head -1 || true)
  [[ "${link}" =~ /(v[0-9]+\.[0-9]+\.[0-9]+) ]] && agents_tags[i]="${BASH_REMATCH[1]}"
  if [[ -n "${agents_tags[i]}" ]]; then
    if ! agents_body=$(gh api "repos/fullsend-ai/agents/releases/tags/${agents_tags[i]}" --jq '.body // ""'); then
      echo "::error::could not read the fullsend-ai/agents ${agents_tags[i]} release that fullsend ${tags[i]} links to" >&2
      exit 1
    fi
    filter_agents_body <<< "${agents_body}" > "${agents_dir}/${i}"
  fi
done

# The agents notes are a few short lines per release, and a cut could drop a
# breaking change, so they are never cut: their bytes come off the budget
# first. The fullsend notes then share one cap, as high as the rest of the
# budget allows: short releases are kept whole and the bytes they leave go to
# the long ones. An even split would cut a long release even when the total
# fits. At least 2000 bytes.
agents_bytes=$(find "${agents_dir}" -type f -exec cat {} + | wc -c)
sizes=$(for f in "${bodies}"/*; do wc -c < "${f}"; done | sort -n)
cap=""
left=$(( MAX_NOTES_BYTES - agents_bytes ))
n=${#tags[@]}
while read -r size; do
  share=$(( left / n ))
  if (( size > share )); then
    cap=$(( share < 2000 ? 2000 : share ))
    break
  fi
  left=$(( left - size ))
  n=$(( n - 1 ))
done <<< "${sizes}"

notes_file="${RUNNER_TEMP:-/tmp}/release-notes.md"
: > "${notes_file}"
for i in "${!tags[@]}"; do
  t="${tags[i]}" a="${agents_tags[i]}"
  {
    printf '## %s (https://github.com/fullsend-ai/fullsend/releases/tag/%s)\n\n' "${t}" "${t}"
    if [[ -n "${cap}" && $(wc -c < "${bodies}/${i}") -gt ${cap} ]]; then
      # iconv drops a UTF-8 sequence cut in half, but still exits 1 when that
      # sequence ends the input, hence `|| true`.
      head -c "${cap}" "${bodies}/${i}" | iconv -c -f UTF-8 -t UTF-8 || true
      printf '\n\n_(release notes truncated)_'
    else
      cat "${bodies}/${i}"
    fi
    printf '\n\n'
    if [[ -n "${a}" ]]; then
      printf '### fullsend-ai/agents %s (https://github.com/fullsend-ai/agents/releases/tag/%s)\n\n' "${a}" "${a}"
      cat "${agents_dir}/${i}"
      printf '\n'
    fi
  } >> "${notes_file}"
done

delim="NOTES_$(openssl rand -hex 8)"
{
  echo "skip=false"
  echo "target_tag=${latest}"
  echo "from_ref=${current}"
  echo "notes<<${delim}"
  cat "${notes_file}"
  echo "${delim}"
} >> "${GITHUB_OUTPUT}"
echo "Upgrade due: ${current} -> ${latest} (${#tags[@]} release(s))"
