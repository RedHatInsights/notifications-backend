# Test PR — GitHub App auth verification

Dummy PR to verify that Jenkins PR performance-test status/comments now
post as the `hce-perfscale-pr-tester` GitHub App instead of a personal
PAT. Safe to close/ignore — no functional code change.

Retest note: re-verifying after bumping the GitHub App's "Pull requests"
permission to Read and write, so the results-table comment can post too.

Retest note 2: re-verifying after fixing NOTIFICATIONS_USE_DEFAULT_TEMPLATE
in the perf ClowdApp config for notifications-connector-email, which was
causing 100% email_runner failures (TemplateNotFoundException).
