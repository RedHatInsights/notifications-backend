# Text that marks the analysis section of the upgrade PR description, defined
# once for the scripts that write, detect and replace it: build-pr-body.sh,
# collect-releases.sh, mark-analysis-failed.sh and
# .fullsend/scripts/post-fullsend-release-watch.sh. Sourced, not run.
# shellcheck shell=bash disable=SC2034
ANALYSIS_HEADING='## 2. Needs human attention'
ANALYSIS_START='<!-- fullsend-release-watch:analysis:start -->'
ANALYSIS_END='<!-- fullsend-release-watch:analysis:end -->'
ANALYSIS_PLACEHOLDER='_The release impact analysis is running. This section is replaced when it finishes._'
