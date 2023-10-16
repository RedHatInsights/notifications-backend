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
# Get the commit SHA to give the scanner a unique "project version" setting.
#
readonly COMMIT_SHORT=$(git rev-parse --short=7 HEAD)

#
# Build the Docker image.
#
docker build \
  --build-arg cacerts_keystore_password="${CACERTS_KEYSTORE_PASSWORD}" \
  --build-arg rh_it_root_ca_cert_url="${RH_IT_ROOT_CA_CERT_URL}" \
  --build-arg rh_it_root_ca_cert_secondary_url="${RH_IT_ROOT_CA_CERT_SECONDARY_URL}" \
  --file .rhcicd/sonarqube/Dockerfile \
  --tag notifications-sonarqube:latest \
  .

#
# Should we be running on master, then skip passing the information regarding
# the pull request.
#
if [ -n "${GIT_BRANCH:-}" ] && [ "${GIT_BRANCH}" == "origin/master" ]; then
  docker run \
    --env COMMIT_SHORT="${COMMIT_SHORT}" \
    --env GIT_BRANCH="${GIT_BRANCH}" \
    --env RH_IT_ROOT_CA_CERT_URL="${RH_IT_ROOT_CA_CERT_URL}" \
    --env RH_IT_ROOT_CA_CERT_SECONDARY_URL="${RH_IT_ROOT_CA_CERT_SECONDARY_URL}" \
    --env SONARQUBE_HOST_URL="${SONARQUBE_HOST_URL}" \
    --env SONARQUBE_TOKEN="${SONARQUBE_TOKEN}" \
    --rm \
    notifications-sonarqube:latest \
    bash .rhcicd/sonarqube/scanner/scan_code.bash
else
  docker run \
    --env COMMIT_SHORT="${COMMIT_SHORT}" \
    --env GIT_BRANCH="${GIT_BRANCH}" \
    --env GITHUB_PULL_REQUEST_ID="${ghprbPullId}" \
    --env RH_IT_ROOT_CA_CERT_URL="${RH_IT_ROOT_CA_CERT_URL}" \
    --env RH_IT_ROOT_CA_CERT_SECONDARY_URL="${RH_IT_ROOT_CA_CERT_SECONDARY_URL}" \
    --env SONARQUBE_HOST_URL="${SONARQUBE_HOST_URL}" \
    --env SONARQUBE_TOKEN="${SONARQUBE_TOKEN}" \
    --rm \
    notifications-sonarqube:latest \
    bash .rhcicd/sonarqube/scanner/scan_code.bash
fi

# Need to make a dummy results file to make the Jenkins tests pass.
mkdir -p artifacts
cat << EOF > artifacts/junit-dummy.xml
<testsuite tests="1">
  <testcase classname="dummy" name="dummytest"/>
</testsuite>
EOF
