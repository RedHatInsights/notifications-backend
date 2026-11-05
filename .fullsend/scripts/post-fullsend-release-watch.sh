#!/usr/bin/env bash
# post-fullsend-release-watch.sh: turn the fullsend-release-watch agent's
# JSON result into one sticky comment on the release's tracking issue.
#
# Started from the `fullsend agent new` (v0.54.0) post-script and adapted:
#   - every status posts the comment, and its first line records the status
#     ("<!-- fullsend-release-watch:status=<status> -->"), because
#     .github/scripts/fullsend-release-watch-poll.sh reads it to decide
#     whether the release is done or must be retried;
#   - status=ok also closes the tracking issue (nothing to act on);
#   - status=findings also adds ready-for-triage, which dispatches the triage
#     agent;
#   - status=error posts the error, then exits 1 so the run fails visibly
#     and the poller retries the release on its next run.
#
# Runs on the trusted runner after the sandbox exits. The working directory is
# the RUN directory, and the agent's output lives one level down, in
# iteration-<N>/output/agent-result.json.
#
# Required env:
#   ISSUE_URL - HTML URL of the work item (the harness maps FULLSEND_WORK_ITEM_URL to it)
#   GH_TOKEN  - minted by fullsend from the harness `role:` field
#
# Optional env:
#   FULLSEND_VALIDATED_ITERATION_DIR - set by fullsend when the
#     validation_loop passed. Always an absolute path; it names the iteration
#     whose output was validated, so it wins over the scan below.
#   POST_FULLSEND_RELEASE_WATCH_DRY_RUN=1 - print the comment instead of
#     posting it, and do not close the issue.
#
# Treat the result file as untrusted: it is model output. Only the three
# fields below are ever read, each must be a string, and the comment body is
# piped on stdin rather than interpolated into a command.

set -euo pipefail

# ${#var} and ${var:0:n} count characters in a UTF-8 locale and BYTES under
# C/POSIX. The result schema's maxLength counts Unicode code points.
export LC_ALL="${LC_ALL:-C.UTF-8}"

: "${ISSUE_URL:?ISSUE_URL must be set}"
: "${GH_TOKEN:?GH_TOKEN must be set}"

# Must match RESULT_MARKER in .github/scripts/fullsend-release-watch-poll.sh.
MARKER="<!-- fullsend:fullsend-release-watch-agent -->"
MAX_SUMMARY_CHARS=200
MAX_COMMENT_CHARS=16384
DRY_RUN="${POST_FULLSEND_RELEASE_WATCH_DRY_RUN:-}"

# Locate the result. FULLSEND_VALIDATED_ITERATION_DIR is set by fullsend on
# the runner, not by the sandbox or the agent, so it is trusted as given.
RESULT_FILE=""
if [[ -n "${FULLSEND_VALIDATED_ITERATION_DIR:-}" ]]; then
  if [[ -f "${FULLSEND_VALIDATED_ITERATION_DIR}/agent-result.json" ]]; then
    RESULT_FILE="${FULLSEND_VALIDATED_ITERATION_DIR}/agent-result.json"
  else
    echo "post-fullsend-release-watch: FULLSEND_VALIDATED_ITERATION_DIR is set but has no agent-result.json" >&2
    exit 1
  fi
else
  # Take the HIGHEST-numbered iteration that produced output, compared
  # numerically (the glob would put iteration-10 before iteration-9).
  _best=-1
  for _dir in iteration-*/output; do
    [[ -f "${_dir}/agent-result.json" ]] || continue
    _n="${_dir%/output}"
    _n="${_n#iteration-}"
    [[ "${_n}" =~ ^[0-9]+$ ]] || continue
    if (( _n > _best )); then
      _best="${_n}"
      RESULT_FILE="${_dir}/agent-result.json"
    fi
  done
fi

if [[ -z "${RESULT_FILE}" || ! -f "${RESULT_FILE}" ]]; then
  echo "post-fullsend-release-watch: no agent-result.json found in any iteration-*/output directory" >&2
  exit 1
fi
if ! jq -e 'type == "object"' "${RESULT_FILE}" >/dev/null 2>&1; then
  echo "post-fullsend-release-watch: ${RESULT_FILE} is not a JSON object" >&2
  exit 1
fi

# Field allowlist: anything else in the file is ignored by construction.
status=$(jq -r 'if (.status | type) == "string" then .status else empty end' "${RESULT_FILE}")
summary=$(jq -r 'if (.summary | type) == "string" then .summary else empty end' "${RESULT_FILE}")
comment=$(jq -r 'if (.comment | type) == "string" then .comment else empty end' "${RESULT_FILE}")
if ! jq -e '(has("comment") | not) or (.comment | type) == "string"' "${RESULT_FILE}" >/dev/null; then
  echo "post-fullsend-release-watch: comment must be a string" >&2
  exit 1
fi

case "${status}" in
  ok|findings|error) ;;
  *)
    # status is model output: never echo it raw. Flatten CR/LF and cap it so
    # it cannot start a new log line (a "::" workflow command), and use
    # printf, which never interprets backslash escapes.
    shown="${status//[$'\r\n']/ }"
    printf '%s\n' "post-fullsend-release-watch: status must be ok, findings or error (got '${shown:0:40}')" >&2
    exit 1
    ;;
esac
[[ -n "${summary}" ]] || { echo "post-fullsend-release-watch: summary is required" >&2; exit 1; }
if [[ "${summary}" == *$'\n'* || "${summary}" == *$'\r'* ]]; then
  echo "post-fullsend-release-watch: summary must be a single line" >&2
  exit 1
fi
if [[ "${status}" != "ok" && -z "${comment}" ]]; then
  echo "post-fullsend-release-watch: comment is required when status is ${status}" >&2
  exit 1
fi
if (( ${#summary} > MAX_SUMMARY_CHARS )); then
  echo "post-fullsend-release-watch: summary exceeds ${MAX_SUMMARY_CHARS} characters" >&2
  exit 1
fi
# Truncate to MAX_COMMENT_CHARS *including* the marker, so the result never
# exceeds the cap the schema declares.
TRUNCATION_MARKER=$'\n\n_(truncated)_'
if (( ${#comment} > MAX_COMMENT_CHARS )); then
  keep=$(( MAX_COMMENT_CHARS - ${#TRUNCATION_MARKER} ))
  if (( keep < 0 )); then keep=0; fi
  comment="${comment:0:${keep}}${TRUNCATION_MARKER}"
fi

# Derive the repo and number from the work item URL rather than trusting extra
# environment. The pattern also rejects anything that is not a github.com
# issue URL (the poller always dispatches for an issue).
url_re='^https://github[.]com/([A-Za-z0-9._-]+/[A-Za-z0-9._-]+)/issues/([0-9]+)$'
if [[ ! "${ISSUE_URL}" =~ ${url_re} ]]; then
  echo "post-fullsend-release-watch: ISSUE_URL is not a GitHub issue URL" >&2
  exit 1
fi
REPO="${BASH_REMATCH[1]}"
NUMBER="${BASH_REMATCH[2]}"

case "${status}" in
  ok) heading="No impact" ;;
  findings) heading="Action needed" ;;
  error) heading="Analysis failed (will be retried on the next poll)" ;;
esac

# The status line must stay the first line of the body. The format is chosen
# here, never taken from the result file. The summary is printed with %s,
# so it is never interpreted as a format.
body_format='<!-- fullsend-release-watch:status=%s -->\n### %s\n\n**%s**\n\n%s\n'
[[ -n "${comment}" ]] || body_format='<!-- fullsend-release-watch:status=%s -->\n### %s\n\n**%s**\n%s'

if [[ "${DRY_RUN}" == "1" ]]; then
  # The preview prints model output. Under GitHub Actions a line starting
  # with "::" is a workflow command, so wrap the preview in a stop-commands
  # block whose token the model cannot guess.
  stop_token=$(od -An -N16 -tx1 /dev/urandom | tr -d ' \n')
  [[ ${#stop_token} -eq 32 ]] || { echo "post-fullsend-release-watch: could not generate a stop-commands token" >&2; exit 1; }
  printf '::stop-commands::%s\n' "${stop_token}"
  # shellcheck disable=SC2059
  printf "${body_format}" "${status}" "${heading}" "${summary}" "${comment}"
  printf '::%s::\n' "${stop_token}"
  echo "post-fullsend-release-watch: dry run, not posting (status=${status})" >&2
else
  # fullsend issues post-comment finds this agent's previous comment by
  # marker and its own author and edits it in place, so re-runs never add a
  # second comment. --keep-history=false keeps exactly one status line in
  # the body, which is what the poller reads.
  # shellcheck disable=SC2059
  printf "${body_format}" "${status}" "${heading}" "${summary}" "${comment}" \
    | fullsend issues post-comment \
        --tracker github \
        --project "${REPO}" \
        --number "${NUMBER}" \
        --marker "${MARKER}" \
        --keep-history=false \
        --result -
fi

# The comment above is what the poller reads, so a failure to change the
# issue's state is only a warning: it never makes the release be retried.
# Neither closing nor reopening starts the fullsend.yaml shim, which listens
# to issues opened, edited and labeled only.
if [[ "${DRY_RUN}" != "1" && "${status}" != "error" ]]; then
  state=$(gh issue view "${NUMBER}" --repo "${REPO}" --json state --jq '.state' 2>/dev/null || true)
  if [[ "${status}" == "ok" && "${state}" == "OPEN" ]]; then
    gh issue close "${NUMBER}" --repo "${REPO}" --reason completed \
      || echo "::warning::post-fullsend-release-watch: could not close issue #${NUMBER}"
  elif [[ "${status}" == "findings" && "${state}" == "CLOSED" ]]; then
    # A forced re-run found something on a release that was closed earlier.
    gh issue reopen "${NUMBER}" --repo "${REPO}" \
      || echo "::warning::post-fullsend-release-watch: could not reopen issue #${NUMBER}"
  fi
fi

# Hand findings to the triage agent. fullsend's GitHub routing dispatches
# triage when ready-for-triage is added, by a human or a bot, so no one has
# to do it by hand.
# GitHub sends no "labeled" event for a label the issue already has, so a
# re-run removes it first (the fullsend.yaml shim ignores "unlabeled").
if [[ "${DRY_RUN}" != "1" && "${status}" == "findings" ]]; then
  TRIAGE_LABEL="ready-for-triage"
  gh issue edit "${NUMBER}" --repo "${REPO}" --remove-label "${TRIAGE_LABEL}" >/dev/null 2>&1 || true
  if ! gh issue edit "${NUMBER}" --repo "${REPO}" --add-label "${TRIAGE_LABEL}"; then
    echo "post-fullsend-release-watch: could not add ${TRIAGE_LABEL} to issue #${NUMBER}" >&2
    exit 1
  fi
fi

case "${status}" in
  error)
    echo "post-fullsend-release-watch: the agent reported an error; failing the run so the release is retried" >&2
    exit 1
    ;;
esac
