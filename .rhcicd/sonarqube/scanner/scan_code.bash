#!/bin/bash

#
# Bash safety options:
#   - e is for exiting immediately when a command exits with a non-zero status.
#   - u is for treating unset variables as an error when substituting.
#   - x is for printing all the commands as they're executed.
#   - o pipefail is for taking into account the exit status of the commands that run on pipelines.
#
set -euxo pipefail

# Start Postgresql and init notifications database
su -l postgres -c /usr/pgsql-11/bin/initdb
su -l postgres -c "/usr/pgsql-11/bin/pg_ctl -D /var/lib/pgsql/11/data -l /tmp/pg_logfile start"
createdb -U postgres notifications

#
# On the master branch there is no need to give the pull request details.
#
if [ -n "${GIT_BRANCH:-}" ] && [ "${GIT_BRANCH}" == "origin/master" ]; then
  ./mvnw clean verify sonar:sonar \
    -Dsonar.host.url="${SONARQUBE_HOST_URL}" \
    -Dsonar.exclusions="**/*.sql" \
    -Dsonar.projectKey="com.redhat.console.notifications.backend" \
    -Dsonar.projectVersion="${COMMIT_SHORT}" \
    -Dsonar.sourceEncoding="UTF-8" \
    -Dsonar.token="${SONARQUBE_TOKEN}" \
    -Dquarkus.devservices.enabled=false \
    --no-transfer-progress
else
  ./mvnw clean verify sonar:sonar \
    -Dsonar.host.url="${SONARQUBE_HOST_URL}" \
    -Dsonar.exclusions="**/*.sql" \
    -Dsonar.projectKey="com.redhat.console.notifications.backend" \
    -Dsonar.projectVersion="${COMMIT_SHORT}" \
    -Dsonar.pullrequest.base="master" \
    -Dsonar.pullrequest.branch="${GIT_BRANCH}" \
    -Dsonar.pullrequest.key="${GITHUB_PULL_REQUEST_ID}" \
    -Dsonar.sourceEncoding="UTF-8" \
    -Dsonar.token="${SONARQUBE_TOKEN}" \
    -Dquarkus.devservices.enabled=false \
    --no-transfer-progress
fi
