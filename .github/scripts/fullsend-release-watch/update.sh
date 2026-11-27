#!/usr/bin/env bash
# Opens ONE pull request with the whole fullsend upgrade: the fullsend_ref
# bump, the files `fullsend repos install` regenerates (including drift
# correction) and the agent harness re-pins.
#
# `repos install` opens the PR but does not deliver repos.yaml or the harness
# files, so they are added to its branch as one signed commit (GraphQL
# createCommitOnBranch).
#
# Env: REPO, TARGET_TAG, FROM_REF, NOTES, GITHUB_OUTPUT, and GH_TOKEN (App
# token), DEFAULT_BRANCH, RUNNER_TEMP, RUN_URL. DRY_RUN=true writes nothing
# to GitHub.
# AGENT_ONLY=true only emits the agent's dispatch matrix (pr_number 0) and
# needs no credentials.
# Outputs: matrix, and pr_number unless AGENT_ONLY.
# Run from the root of a checkout of the default branch.
set -euo pipefail

: "${REPO:?}" "${TARGET_TAG:?}" "${FROM_REF:?}" "${GITHUB_OUTPUT:?}"
NOTES="${NOTES:-}"
DRY_RUN="${DRY_RUN:-false}"
AGENT_ONLY="${AGENT_ONLY:-false}"
WORK="${RUNNER_TEMP:-/tmp}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# The installer names its branch after its own release tag, which is TARGET_TAG.
BRANCH="fullsend/bump-${TARGET_TAG}"
# Open PR from BRANCH in this repo. `gh pr list --head` also matches fork PRs
# with the same branch name, and this script must never edit one of those.
own_pr() {
  gh pr list --repo "${REPO}" --head "${BRANCH}" --state open --json number,isCrossRepository \
    --jq '[.[] | select(.isCrossRepository | not)][0].number // empty'
}

# Dispatch matrix for the analysis agent. reusable-dispatch.yml needs
# event_payload as an object with pull_request.number and .html_url. For an
# agent-only run (pr 0) the URL points at no real PR; nothing fetches it.
emit_matrix() {
  local pr="$1" payload entry delim
  payload=$(jq -c -n --arg tag "${TARGET_TAG}" --arg from_ref "${FROM_REF}" --arg notes "${NOTES}" \
    --argjson pr "${pr}" --arg url "https://github.com/${REPO}/pull/${pr}" \
    '{tag: $tag, from_ref: $from_ref, release_body: $notes, pr_number: $pr,
      pull_request: {number: $pr, html_url: $url}}')
  entry=$(jq -n --arg agent "fullsend-release-watch" --arg repo "${REPO}" --argjson payload "${payload}" \
    '{agent: $agent, source_repo: $repo, role: "review", event_payload: $payload}')
  delim="MATRIX_$(openssl rand -hex 8)"
  {
    echo "matrix<<${delim}"
    jq -c -n --argjson entry "${entry}" '{include: [$entry]}'
    echo "${delim}"
  } >> "${GITHUB_OUTPUT}"
}

if [[ "${AGENT_ONLY}" == "true" ]]; then
  emit_matrix 0
  echo "Agent-only run: no pull request is opened or edited."
  exit 0
fi

: "${GH_TOKEN:?}" "${DEFAULT_BRANCH:?}"

if [[ "${DRY_RUN}" != "true" && "${GITHUB_REF_NAME:-${DEFAULT_BRANCH}}" != "${DEFAULT_BRANCH}" ]]; then
  echo "::error::refusing to open an upgrade PR from ${GITHUB_REF_NAME}; run from ${DEFAULT_BRANCH} or use dry_run" >&2
  exit 1
fi

# --- 1. Install the fullsend CLI for the target release ---------------
# The CLI must match the target release, or the next run "corrects" what this
# one wrote. It runs with the App token, so it is trusted only after the
# release's cosign signature verifies for exactly this tag (see
# docs/cli/self-update.md in fullsend). A checksum alone would not do: it ships
# in the same release.
install_cli() {
  local asset="fullsend_${TARGET_TAG#v}_linux_amd64.tar.gz" dl
  command -v cosign > /dev/null || { echo "::error::cosign is required" >&2; exit 1; }
  dl="$(mktemp -d)"
  gh release download "${TARGET_TAG}" --repo fullsend-ai/fullsend \
    --pattern "${asset}" --pattern checksums.txt --pattern checksums.txt.sigstore.json --dir "${dl}"
  cosign verify-blob "${dl}/checksums.txt" \
    --bundle "${dl}/checksums.txt.sigstore.json" \
    --certificate-oidc-issuer https://token.actions.githubusercontent.com \
    --certificate-identity "https://github.com/fullsend-ai/fullsend/.github/workflows/release.yml@refs/tags/${TARGET_TAG}"
  (cd "${dl}" && grep " ${asset}\$" checksums.txt | sha256sum -c -)
  mkdir -p "${WORK}/fullsend-bin"
  tar -xzf "${dl}/${asset}" -C "${WORK}/fullsend-bin" fullsend
  export PATH="${WORK}/fullsend-bin:${PATH}"
  fullsend --version
}
install_cli

# --- 2. Bump the pins and re-pin the agent harnesses --------------------
fullsend repos set github.fullsend_ref "${TARGET_TAG}" -f repos.yaml

# A pin line in a form the sed below does not expect (a tag ref, quotes, a
# tab, no comment) keeps its old pin without any error. Fails unless file $1
# has a line matching $2 and every such line matches $3.
check_pins() {
  local file="$1" any="$2" want="$3" lines
  lines=$(grep -E -- "${any}" "${file}" || true)
  if [[ -z "${lines}" ]] || grep -vqE -- "${want}" <<< "${lines}"; then
    echo "::error::could not re-pin ${file}: a line matching '${any}' does not match '${want}'" >&2
    exit 1
  fi
}
tag_re="${TARGET_TAG//./\\.}"

# `repos install` does not touch these hand-written workflows, and Renovate
# ignores fullsend, so their pins are rewritten here.
target_sha=$(gh api "repos/fullsend-ai/fullsend/commits/${TARGET_TAG}" --jq '.sha')
[[ "${target_sha}" =~ ^[0-9a-f]{40}$ ]] || { echo "::error::no commit SHA for ${TARGET_TAG}" >&2; exit 1; }
sed -E -i "s|(uses: fullsend-ai/fullsend/\.github/workflows/reusable-dispatch\.yml@)[0-9a-f]{40}( +# *)[^ ]+|\1${target_sha}\2${TARGET_TAG}|" \
  .github/workflows/fullsend-release-watch.yml
check_pins .github/workflows/fullsend-release-watch.yml 'fullsend-ai/fullsend/\.github/workflows/reusable-dispatch\.yml@' \
  "uses: fullsend-ai/fullsend/\.github/workflows/reusable-dispatch\.yml@${target_sha} +# *${tag_re}( |\$)"
sed -E -i -e "s|(uses: fullsend-ai/fullsend@)[0-9a-f]{40}( +# *)[^ ]+|\1${target_sha}\2${TARGET_TAG}|" \
  -e "s|^( +version: )v[0-9][^ ]*\$|\1${TARGET_TAG}|" \
  .github/workflows/fullsend-lint.yml
check_pins .github/workflows/fullsend-lint.yml 'fullsend-ai/fullsend@' "uses: fullsend-ai/fullsend@${target_sha} +# *${tag_re}( |\$)"
check_pins .github/workflows/fullsend-lint.yml '^ +version:' "^ +version: ${tag_re}\$"

# fullsend-ai/agents is tagged with every fullsend release. Without a SHA,
# `agent update` would pin a local harness base: to agents main instead.
agents_sha=$(gh api "repos/fullsend-ai/agents/commits/${TARGET_TAG}" --jq '.sha')
[[ "${agents_sha}" =~ ^[0-9a-f]{40}$ ]] || { echo "::error::no fullsend-ai/agents commit for ${TARGET_TAG}" >&2; exit 1; }

# Captured first, not read through `< <(...)`: set -e ignores a failing
# process substitution, which would skip every re-pin without an error.
agents=$(fullsend agent list --fullsend-dir .fullsend | awk 'NR > 1 {print $1}')
while read -r agent; do
  [[ -n "${agent}" ]] || continue
  if out=$(fullsend agent update "${agent}" "${agents_sha}" --fullsend-dir .fullsend 2>&1); then
    echo "re-pinned agent ${agent}"
  elif grep -q 'nothing to update' <<< "${out}"; then
    echo "agent ${agent}: no upstream base to re-pin"
  else
    echo "::error::agent update ${agent} failed: ${out}" >&2
    exit 1
  fi
done <<< "${agents}"

# The release-watch harness has no base:, so `agent update` skips it. Pin its
# sandbox image to the fleet triage harness image of the same agents release.
image=$(gh api -H 'Accept: application/vnd.github.raw' \
  "repos/fullsend-ai/agents/contents/harness/triage.yaml?ref=${agents_sha}" | sed -n 's/^image: *//p')
if ! [[ "${image}" =~ ^ghcr\.io/fullsend-ai/fullsend-sandbox@sha256:[0-9a-f]{64}$ ]]; then
  echo "::error::no fullsend-sandbox image pin in fullsend-ai/agents harness/triage.yaml at ${agents_sha}" >&2
  exit 1
fi
sed -E -i "s|^image: .*\$|image: ${image}|" .fullsend/harness/fullsend-release-watch.yaml
check_pins .fullsend/harness/fullsend-release-watch.yaml '^image:' "^image: ${image//./\\.}\$"

# --- 3. Regenerate managed files and correct drift ----------------------
if [[ "${DRY_RUN}" == "true" ]]; then
  fullsend repos install -f repos.yaml --dry-run
  echo "::group::Local edits that would be added to the PR branch"
  git --no-pager diff --stat
  echo "::endgroup::"
  echo "Dry run: nothing was written to GitHub."
  exit 0
fi

fullsend repos install -f repos.yaml

# `repos install` has opened the PR. If a later step fails, say so on the PR:
# it may lack repos.yaml, the pins or the analysis, and must not be merged as
# is. A scheduled run completes it only while its description has no analysis
# markers (see collect-releases.sh), and this run may already have written
# them, so the comment asks for a `force` run, which works either way.
# `finalize` does not run when this job fails, so the placeholder is replaced
# here.
early_pr=$(own_pr || true)
on_exit() {
  local rc=$? pr
  if [[ ${rc} -ne 0 ]]; then
    # Looked up again: `gh pr create` can fail after it created the PR.
    pr=$(own_pr || true)
    pr="${pr:-${early_pr}}"
    if [[ -n "${pr}" ]]; then
      gh pr comment "${pr}" --repo "${REPO}" --body "**This upgrade PR is incomplete.** The release-watch run failed after \`fullsend repos install\` opened it (${RUN_URL:-see the workflow runs}). Do not merge it. Re-run the workflow with \`force\` to complete it." || true
      PR_NUMBER="${pr}" RUN_URL="${RUN_URL:-see the workflow runs}" "${SCRIPT_DIR}/mark-analysis-failed.sh" || true
    fi
  fi
  exit "${rc}"
}
trap on_exit EXIT

# --- 4. Make sure the upgrade branch exists -----------------------------
if ! head_sha=$(gh api "repos/${REPO}/git/ref/heads/${BRANCH}" --jq '.object.sha' 2>/dev/null); then
  # The installer had nothing to deliver, but the local edits still need a branch.
  default_sha=$(gh api "repos/${REPO}/git/ref/heads/${DEFAULT_BRANCH}" --jq '.object.sha')
  gh api -X POST "repos/${REPO}/git/refs" -f "ref=refs/heads/${BRANCH}" -f "sha=${default_sha}" > /dev/null
  head_sha="${default_sha}"
  echo "created ${BRANCH} from ${DEFAULT_BRANCH}"
fi

# --- 5. Add the local edits to the branch as one signed commit ----------
to_commit=()
changed=$(git status --porcelain --untracked-files=all | sed -E 's/^.{3}//')
while IFS= read -r path; do
  [[ -n "${path}" ]] || continue
  if [[ ! -f "${path}" ]]; then
    echo "::error::${path} was deleted locally; deletions are not supported" >&2
    exit 1
  fi
  remote="$(mktemp)"
  if gh api -H 'Accept: application/vnd.github.raw' "repos/${REPO}/contents/${path}?ref=${BRANCH}" > "${remote}" 2>/dev/null \
    && cmp -s "${remote}" "${path}"; then
    echo "${path}: already up to date on ${BRANCH}"
  else
    to_commit+=("${path}")
  fi
done <<< "${changed}"

if [[ ${#to_commit[@]} -gt 0 ]]; then
  additions=$(for p in "${to_commit[@]}"; do
    jq -n --arg path "${p}" --arg contents "$(base64 -w0 "${p}")" '{path: $path, contents: $contents}'
  done | jq -s '.')
  jq -n \
    --arg repo "${REPO}" --arg branch "${BRANCH}" --arg oid "${head_sha}" \
    --arg headline "chore: update fullsend manifest and agent pins for ${TARGET_TAG}" \
    --argjson additions "${additions}" \
    '{
      query: "mutation($input: CreateCommitOnBranchInput!) { createCommitOnBranch(input: $input) { commit { oid url } } }",
      variables: { input: {
        branch: { repositoryNameWithOwner: $repo, branchName: $branch },
        message: { headline: $headline },
        expectedHeadOid: $oid,
        fileChanges: { additions: $additions }
      } }
    }' | gh api graphql --input - --jq '.data.createCommitOnBranch.commit.url'
  echo "committed: ${to_commit[*]}"
fi

# --- 6. Create or update the pull request -------------------------------
git --no-pager diff -U0 --no-color -- .fullsend/harness > "${WORK}/harness.diff"
gh api "repos/${REPO}/compare/${DEFAULT_BRANCH}...${BRANCH}" \
  --jq '.files[] | [.status, .filename, .additions, .deletions] | @tsv' > "${WORK}/files.tsv"

"${SCRIPT_DIR}/build-pr-body.sh" "${FROM_REF}" "${TARGET_TAG}" "${WORK}/files.tsv" \
  "${WORK}/harness.diff" > "${WORK}/pr-body.md"

pr_number=$(own_pr)
if [[ -z "${pr_number}" ]]; then
  pr_url=$(gh pr create --repo "${REPO}" --head "${BRANCH}" --base "${DEFAULT_BRANCH}" \
    --title "chore: bump fullsend from ${FROM_REF} to ${TARGET_TAG}" --body-file "${WORK}/pr-body.md")
  pr_number="${pr_url##*/}"
else
  gh pr edit "${pr_number}" --repo "${REPO}" --body-file "${WORK}/pr-body.md"
  pr_url="https://github.com/${REPO}/pull/${pr_number}"
fi
echo "pull request: ${pr_url}"

# --- 7. Hand over to the analysis agent ---------------------------------
echo "pr_number=${pr_number}" >> "${GITHUB_OUTPUT}"
emit_matrix "${pr_number}"

# Recorded once the PR exists, not when the analysis finishes, so a PR closed
# without merging is not proposed again every day. GITHUB_TOKEN cannot write
# variables; the App token can. Not fatal: the PR is complete by now, and a
# failure here would skip the analysis while the PR blocks later runs.
# No FULLSEND_ prefix: `fullsend repos status` reports unknown FULLSEND_*
# variables as drift.
if ! gh variable set RELEASE_WATCH_LAST_TAG --repo "${REPO}" --body "${TARGET_TAG}"; then
  echo "::warning::could not record ${TARGET_TAG} in RELEASE_WATCH_LAST_TAG; if ${pr_url} is closed without merging, the next run proposes ${TARGET_TAG} again"
fi
