#!/usr/bin/env bash
# pre-fullsend-release-watch.sh: collect the input the fullsend-release-watch
# agent analyzes, on the trusted runner, before the sandbox starts.
#
# The poller (.github/scripts/fullsend-release-watch-poll.sh) puts the
# upgrade range in the dispatch payload as release_watch.from (the fullsend
# version this repository runs) and release_watch.to (the latest release).
# This script writes one Markdown file with:
#   - the range and a compare link;
#   - references the agent cannot look up from inside its sandbox (it has no
#     GitHub access): the fullsend commit for `to`, the fullsend-ai/agents
#     release named in `to`'s notes, that release's commit, and the sandbox
#     image its harness/triage.yaml pins. A value that cannot be resolved is
#     written as "not resolved", never guessed;
#   - how fullsend is installed: whether .fullsend/config.yaml is managed by
#     `fullsend repos install` (rendered from a repos.yaml manifest), and the
#     manifest's fullsend_ref when the manifest is in this repository;
#   - every pin in the repository that the upgrade moves, with its current
#     value and its exact target: fullsend-ai/fullsend workflow refs in
#     .github/workflows/, fullsend-ai/agents `base:` URLs in
#     .fullsend/harness/ (with the target file's recomputed sha256, the same
#     value `fullsend agent update` writes), and fullsend sandbox image
#     digests. The upgrade itself is the agent's first action item, built
#     from this list;
#   - the release notes of every published release after `from` up to and
#     including `to`, oldest first.
# The harness copies that file into the sandbox through host_files.
#
# Required env:
#   FULLSEND_EVENT_PAYLOAD path of the dispatch payload (the harness sets it
#                          to ${FULLSEND_DIR}/dispatch/event-payload.json)
#   RELEASE_WATCH_REPO_DIR checkout of the repository the agent analyzes (the
#                          harness sets it to reusable-dispatch.yml's
#                          checkout at the ref that started the run, the
#                          default branch on the schedule:
#                          ${FULLSEND_DIR}/../target-repo)
#   GH_TOKEN               any token that can read public repositories
# Optional env:
#   RELEASE_WATCH_INPUT    output file (default
#                          /tmp/workspace/fullsend-release-watch/release-notes.md;
#                          must match host_files in the harness)

set -euo pipefail
export LC_ALL="${LC_ALL:-C.UTF-8}"

: "${FULLSEND_EVENT_PAYLOAD:?FULLSEND_EVENT_PAYLOAD must be set}"
: "${GH_TOKEN:?GH_TOKEN must be set}"
: "${RELEASE_WATCH_REPO_DIR:?RELEASE_WATCH_REPO_DIR must be set}"
OUT="${RELEASE_WATCH_INPUT:-/tmp/workspace/fullsend-release-watch/release-notes.md}"
if [[ ! -d "${RELEASE_WATCH_REPO_DIR}/.github/workflows" ]]; then
  echo "::error::no .github/workflows directory in RELEASE_WATCH_REPO_DIR (${RELEASE_WATCH_REPO_DIR})" >&2
  exit 1
fi

UPSTREAM="fullsend-ai/fullsend"
# First line of a .fullsend/config.yaml that `fullsend repos install` manages
# (fullsend ADR-0122).
MANAGED_CONFIG_MARKER="# This file is managed by 'fullsend repos': repos-config-v1"
AGENTS_REPO="fullsend-ai/agents"
JQ_SEMVER='def semver: test("^v[0-9]+\\.[0-9]+\\.[0-9]+$");
           def ver: ltrimstr("v") | split(".") | map(tonumber);'

if [[ ! -f "${FULLSEND_EVENT_PAYLOAD}" ]]; then
  echo "::error::dispatch payload not found at ${FULLSEND_EVENT_PAYLOAD}" >&2
  exit 1
fi
from=$(jq -r '.release_watch.from // empty' "${FULLSEND_EVENT_PAYLOAD}")
to=$(jq -r '.release_watch.to // empty' "${FULLSEND_EVENT_PAYLOAD}")
semver_re='^v[0-9]+\.[0-9]+\.[0-9]+$'
if [[ ! "${from}" =~ ${semver_re} || ! "${to}" =~ ${semver_re} ]]; then
  echo "::error::dispatch payload has no valid release_watch.from/to (vX.Y.Z)" >&2
  exit 1
fi

# Every published release in (from, to], oldest first.
releases=$(gh api --paginate "repos/${UPSTREAM}/releases?per_page=100" \
  | jq -cs --arg from "${from}" --arg to "${to}" "${JQ_SEMVER}"'
      add // []
      | [.[] | select(.draft == false and .prerelease == false and (.tag_name | semver))
             | select((.tag_name | ver) > ($from | ver) and (.tag_name | ver) <= ($to | ver))
             | {tag: .tag_name, url: .html_url, published_at, body: (.body // "")}]
      | sort_by(.tag | ver)')
if [[ "$(jq -r --arg to "${to}" 'map(.tag) | index($to) != null' <<< "${releases}")" != "true" ]]; then
  echo "::error::release ${to} not found in ${UPSTREAM}" >&2
  exit 1
fi
to_notes=$(jq -r --arg to "${to}" '.[] | select(.tag == $to) | .body' <<< "${releases}")

# References for `to`. Keep only values of the expected shape, so an API
# error message or an unexpected file layout is reported as unresolved.
fs_sha=$(gh api "repos/${UPSTREAM}/commits/${to}" --jq '.sha' 2>/dev/null || true)
agents_tag=$(grep -oE "https://github\.com/${AGENTS_REPO}/releases/tag/v[0-9]+\.[0-9]+\.[0-9]+" <<< "${to_notes}" \
  | head -n 1 | sed 's#.*/##' || true)
agents_sha=""
image=""
if [[ -n "${agents_tag}" ]]; then
  agents_sha=$(gh api "repos/${AGENTS_REPO}/commits/${agents_tag}" --jq '.sha' 2>/dev/null || true)
fi
[[ "${fs_sha}" =~ ^[0-9a-f]{40}$ ]] || fs_sha=""
[[ "${agents_sha}" =~ ^[0-9a-f]{40}$ ]] || agents_sha=""
if [[ -n "${agents_sha}" ]]; then
  image=$(gh api "repos/${AGENTS_REPO}/contents/harness/triage.yaml?ref=${agents_sha}" --jq '.content' 2>/dev/null \
    | base64 -d 2>/dev/null \
    | sed -nE 's/^image:[[:space:]]*([^[:space:]#]+).*/\1/p' | head -n 1 || true)
fi
[[ "${image}" =~ ^[a-z0-9./_-]+@sha256:[0-9a-f]{64}$ ]] || image=""

# Prints its argument as a Markdown code span, or "not resolved" when empty.
# shellcheck disable=SC2016
code_or_unresolved() {
  if [[ -n "$1" ]]; then printf '`%s`' "$1"; else printf 'not resolved'; fi
}

# sha256 of a file's raw bytes (the integrity hash in a `base:` URL).
sha256_of_stdin() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum | cut -d' ' -f1; else shasum -a 256 | cut -d' ' -f1; fi
}

# Prints the "Pins this upgrade moves" section: one list item per pin found
# in the repository, with its current value and its target. Paths are
# relative to the repository root.
# shellcheck disable=SC2016
pins_section() {
  local repo="${RELEASE_WATCH_REPO_DIR}" f rel line_no line ref_path sha comment target managed
  local found=false
  printf '## Pins this upgrade moves\n\n'
  printf 'Read from the checkout this analysis runs on (the default branch on scheduled runs). "current" means the pin already has its target value.\n\n'

  printf '### fullsend installation (`.fullsend/config.yaml`, `repos.yaml`)\n\n'
  local cfg="${repo}/.fullsend/config.yaml" source_line
  if [[ -f "${cfg}" ]] && [[ "$(head -n 1 "${cfg}")" == "${MANAGED_CONFIG_MARKER}" ]]; then
    printf -- '- `.fullsend/config.yaml` is managed by `fullsend repos install`: it is rendered from the `config:` block of this repository'"'"'s entry in a `repos.yaml` manifest, and editing it directly is overwritten by the next install. Configuration changes go to the manifest, followed by `fullsend repos install`.\n'
    source_line=$(sed -n '3p' "${cfg}")
    if [[ "${source_line}" == "# Source manifest: "* ]]; then
      printf -- '- Source manifest named in `.fullsend/config.yaml`: `%s`\n' "${source_line#"# Source manifest: "}"
    fi
  elif [[ -f "${cfg}" ]]; then
    printf -- '- `.fullsend/config.yaml` is not managed by `fullsend repos install` (no ownership marker): configuration changes are made in that file directly.\n'
  else
    printf -- '- `.fullsend/config.yaml` not found.\n'
  fi
  if [[ -f "${repo}/repos.yaml" ]]; then
    found=false
    while IFS= read -r hit; do
      line_no="${hit%%:*}"
      ref_path=$(sed -nE "s#^[[:space:]]*fullsend_ref:[[:space:]]*[\"']?([^\"'[:space:]\#]+).*#\1#p" <<< "${hit#*:}")
      [[ -n "${ref_path}" ]] || continue
      found=true
      if [[ "${ref_path}" == "${to}" ]]; then
        printf -- '- `repos.yaml:%s`: `fullsend_ref: %s`: current\n' "${line_no}" "${ref_path}"
      else
        printf -- '- `repos.yaml:%s`: `fullsend_ref: %s` → `fullsend_ref: %s` (the manifest pin `fullsend repos install` renders the managed files from)\n' "${line_no}" "${ref_path}" "${to}"
      fi
    done < <(grep -nE '^[[:space:]]*fullsend_ref:' "${repo}/repos.yaml" || true)
    [[ "${found}" == "true" ]] || printf -- '- `repos.yaml` declares no `fullsend_ref`.\n'
  else
    printf -- '- No `repos.yaml` manifest in this repository.\n'
  fi
  found=false
  printf '\n'

  printf '### fullsend-ai/fullsend workflow refs (`.github/workflows/`)\n\n'
  for f in "${repo}"/.github/workflows/*.yml "${repo}"/.github/workflows/*.yaml; do
    [[ -f "${f}" ]] || continue
    rel="${f#"${repo}"/}"
    managed=""
    if grep -q "This file is managed by fullsend" "${f}"; then
      managed=' (managed by fullsend: its header says not to edit it by hand; `fullsend repos install` rewrites it from the manifest'"'"'s `fullsend_ref`)'
    fi
    while IFS= read -r hit; do
      line_no="${hit%%:*}"
      line="${hit#*:}"
      ref_path=$(sed -nE "s#.*uses:[[:space:]]*(${UPSTREAM}/[A-Za-z0-9._/-]+)@.*#\1#p" <<< "${line}")
      sha=$(sed -nE 's#.*@([0-9a-f]{40})([[:space:]].*)?$#\1#p' <<< "${line}")
      comment=$(sed -nE 's#.*@[0-9a-f]{40}[[:space:]]+\#[[:space:]]*(.*[^[:space:]])[[:space:]]*$#\1#p' <<< "${line}")
      found=true
      if [[ -z "${sha}" ]]; then
        printf -- '- `%s:%s`: `%s` is not pinned to a commit SHA (`%s`); not a release pin%s\n' \
          "${rel}" "${line_no}" "${ref_path:-${UPSTREAM}}" "$(sed -E 's/^[[:space:]]+//' <<< "${line}")" "${managed}"
      elif [[ -n "${fs_sha}" && "${sha}" == "${fs_sha}" ]]; then
        printf -- '- `%s:%s`: `%s@%s` (`%s`): current%s\n' "${rel}" "${line_no}" "${ref_path}" "${sha}" "${comment:-no tag comment}" "${managed}"
      else
        target="not resolved"
        [[ -n "${fs_sha}" ]] && target="\`${ref_path}@${fs_sha}  # ${to}\`"
        printf -- '- `%s:%s`: `%s@%s` (`%s`) → %s%s\n' "${rel}" "${line_no}" "${ref_path}" "${sha}" "${comment:-no tag comment}" "${target}" "${managed}"
      fi
    done < <(grep -nE "^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]*${UPSTREAM}/" "${f}" || true)
  done
  [[ "${found}" == "true" ]] || printf -- '- none found\n'

  printf '\n### fullsend-ai/agents `base:` pins (`.fullsend/harness/`)\n\n'
  found=false
  for f in "${repo}"/.fullsend/harness/*.yml "${repo}"/.fullsend/harness/*.yaml; do
    [[ -f "${f}" ]] || continue
    rel="${f#"${repo}"/}"
    while IFS= read -r hit; do
      line_no="${hit%%:*}"
      line="${hit#*:}"
      sha=$(sed -nE "s#^base:[[:space:]]*https://raw\.githubusercontent\.com/${AGENTS_REPO}/([0-9a-f]{40})/.*#\1#p" <<< "${line}")
      ref_path=$(sed -nE "s#^base:[[:space:]]*https://raw\.githubusercontent\.com/${AGENTS_REPO}/[0-9a-f]{40}/([A-Za-z0-9._/-]+)\#sha256=[0-9a-f]{64}[[:space:]]*\$#\1#p" <<< "${line}")
      [[ -n "${sha}" && -n "${ref_path}" && "${ref_path}" != *..* ]] || continue
      found=true
      if [[ -n "${agents_sha}" && "${sha}" == "${agents_sha}" ]]; then
        printf -- '- `%s:%s`: `%s` at `%s`: current\n' "${rel}" "${line_no}" "${ref_path}" "${sha}"
        continue
      fi
      target="not resolved"
      if [[ -n "${agents_sha}" ]]; then
        local new_hash
        new_hash=$(gh api -H "Accept: application/vnd.github.raw" "repos/${AGENTS_REPO}/contents/${ref_path}?ref=${agents_sha}" 2>/dev/null \
          | sha256_of_stdin || true)
        if [[ "${new_hash}" =~ ^[0-9a-f]{64}$ && "${new_hash}" != "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" ]]; then
          target="\`https://raw.githubusercontent.com/${AGENTS_REPO}/${agents_sha}/${ref_path}#sha256=${new_hash}\`"
        else
          target="not resolved (\`${ref_path}\` could not be fetched at \`${agents_sha}\`)"
        fi
      fi
      printf -- '- `%s:%s`: `%s` at `%s` → %s\n' "${rel}" "${line_no}" "${ref_path}" "${sha}" "${target}"
    done < <(grep -nE '^base:[[:space:]]*https://' "${f}" || true)
  done
  [[ "${found}" == "true" ]] || printf -- '- none found\n'

  printf '\n### fullsend sandbox image digests (`.fullsend/harness/`)\n\n'
  found=false
  for f in "${repo}"/.fullsend/harness/*.yml "${repo}"/.fullsend/harness/*.yaml; do
    [[ -f "${f}" ]] || continue
    rel="${f#"${repo}"/}"
    while IFS= read -r hit; do
      line_no="${hit%%:*}"
      line=$(sed -nE 's#^image:[[:space:]]*(ghcr\.io/fullsend-ai/fullsend-sandbox@sha256:[0-9a-f]{64}).*#\1#p' <<< "${hit#*:}")
      [[ -n "${line}" ]] || continue
      found=true
      if [[ -n "${image}" && "${line}" == "${image}" ]]; then
        printf -- '- `%s:%s`: `%s`: current\n' "${rel}" "${line_no}" "${line}"
      else
        printf -- '- `%s:%s`: `%s` → %s\n' "${rel}" "${line_no}" "${line}" "$(code_or_unresolved "${image}")"
      fi
    done < <(grep -nE '^image:[[:space:]]*' "${f}" || true)
  done
  [[ "${found}" == "true" ]] || printf -- '- none found\n'
}

mkdir -p "$(dirname "${OUT}")"
tmp="${OUT}.tmp"
# shellcheck disable=SC2016
{
  printf '# fullsend upgrade %s → %s\n\n' "${from}" "${to}"
  printf -- '- Upgrade from: `%s` (the oldest fullsend workflow pin in this repository'"'"'s `.github/workflows/`)\n' "${from}"
  printf -- '- Upgrade to: `%s` (the latest published release)\n' "${to}"
  printf -- '- Releases in the range, oldest first: %s\n' "$(jq -r 'map("`" + .tag + "`") | join(", ")' <<< "${releases}")"
  printf -- '- Compare: https://github.com/%s/compare/%s...%s\n\n' "${UPSTREAM}" "${from}" "${to}"
  printf '## Resolved references for %s\n\n' "${to}"
  printf -- '- fullsend-ai/fullsend `%s` commit: %s\n' "${to}" "$(code_or_unresolved "${fs_sha}")"
  if [[ -n "${agents_tag}" ]]; then
    printf -- '- fullsend-ai/agents release named in the `%s` notes: `%s`\n' "${to}" "${agents_tag}"
  else
    printf -- '- fullsend-ai/agents release named in the `%s` notes: not found\n' "${to}"
  fi
  printf -- '- fullsend-ai/agents release commit: %s\n' "$(code_or_unresolved "${agents_sha}")"
  printf -- '- Sandbox image pinned by fullsend-ai/agents `harness/triage.yaml` at that commit: %s\n\n' "$(code_or_unresolved "${image}")"
  pins_section
  while IFS= read -r rel; do
    printf '\n\n---\n\n# Release notes: %s\n\n' "$(jq -r '.tag' <<< "${rel}")"
    printf 'Published %s. %s\n\n' "$(jq -r '.published_at' <<< "${rel}")" "$(jq -r '.url' <<< "${rel}")"
    printf '%s\n' "$(jq -r '.body' <<< "${rel}")"
  done < <(jq -c '.[]' <<< "${releases}")
} > "${tmp}"
mv "${tmp}" "${OUT}"
echo "Wrote ${OUT}: ${from} → ${to}, $(jq 'length' <<< "${releases}") release(s)."
