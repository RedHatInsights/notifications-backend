#!/bin/bash

#
# Bash safety options:
#   - e is for exiting immediately when a command exits with a non-zero status.
#   - u is for treating unset variables as an error when substituting.
#   - x is for printing all the commands as they're executed.
#   - o pipefail is for taking into account the exit status of the commands that run on pipelines.
#
set -euxo pipefail

#
# On the master branch there is no need to give the pull request details.
#
if [ -n "${GIT_BRANCH:-}" ] && [ "${GIT_BRANCH}" == "master" ]; then
  ./mvnw clean compile sonar:sonar \
    -Dsonar.host.url="${SONARQUBE_HOST_URL}" \
    -Dsonar.exclusions="**/*.sql" \
    -Dsonar.projectKey="com.redhat.console.notifications.backend" \
    -Dsonar.projectVersion="${COMMIT_SHORT}" \
    -Dsonar.sourceEncoding="UTF-8" \
    -Dsonar.token="${SONARQUBE_TOKEN}"
else
  ./mvnw clean compile sonar:sonar \
    -Dsonar.host.url="${SONARQUBE_HOST_URL}" \
    -Dsonar.exclusions="**/*.sql" \
    -Dsonar.projectKey="com.redhat.console.notifications.backend" \
    -Dsonar.projectVersion="${COMMIT_SHORT}" \
    -Dsonar.pullrequest.base="master" \
    -Dsonar.pullrequest.branch="${GIT_BRANCH}" \
    -Dsonar.pullrequest.key="${GITHUB_PULL_REQUEST_ID}" \
    -Dsonar.sourceEncoding="UTF-8" \
    -Dsonar.token="${SONARQUBE_TOKEN}"
fi
