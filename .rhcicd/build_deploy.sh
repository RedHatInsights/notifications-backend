#!/bin/bash

set -exv

if [[ -z "$QUAY_USER" || -z "$QUAY_TOKEN" ]]; then
    echo "QUAY_USER and QUAY_TOKEN must be set"
    exit 1
fi

# Create a temporary directory isolated from $WORKSPACE to store the Docker config and prevent leaking that config in the Docker image.
readonly job_directory_path=$(mktemp --directory -p "${HOME}" -t "jenkins-${JOB_NAME}-${BUILD_NUMBER}.XXXXXX")
echo "Temporary directory location for the Docker config: ${job_directory_path}"

# job_directory_cleanup cleans forcefully and recursively removes the
#                       specified directory path.
# @param directory_path the path of the directory to remove.
function job_directory_cleanup() {
  echo "Cleaning up the temporary directory for the Docker config: ${1}"

  rm --force --recursive "${1}"
}

# Make sure the temporary directory and all of its contents are removed in the
# case where this script does not finish successfully.
trap 'job_directory_cleanup ${job_directory_path}' ERR EXIT SIGINT SIGTERM

# Place our docker configuration in the freshly created temporary directory.
DOCKER_CONF="${job_directory_path}/.docker"
mkdir -p "$DOCKER_CONF"

IMAGE_TAG=$(git rev-parse --short=7 HEAD)
SECURITY_COMPLIANCE_TAG="sc-$(date +%Y%m%d)-$(git rev-parse --short=7 HEAD)"

function buildAndDeploy() {
    IMAGE_NAME=$1
    IMAGE="quay.io/cloudservices/${IMAGE_NAME}"
    docker --config="$DOCKER_CONF" login -u="$QUAY_USER" -p="$QUAY_TOKEN" quay.io
    docker --config="$DOCKER_CONF" build -t "${IMAGE}:${IMAGE_TAG}" . -f docker/Dockerfile.${IMAGE_NAME}.jvm
    docker --config="$DOCKER_CONF" push "${IMAGE}:${IMAGE_TAG}"
    if [[ $GIT_BRANCH == *"security-compliance"* ]]; then
        docker --config="$DOCKER_CONF" tag "${IMAGE}:${IMAGE_TAG}" "${IMAGE}:${SECURITY_COMPLIANCE_TAG}"
        docker --config="$DOCKER_CONF" push "${IMAGE}:${SECURITY_COMPLIANCE_TAG}"
    else
        docker --config="$DOCKER_CONF" tag "${IMAGE}:${IMAGE_TAG}" "${IMAGE}:qa"
        docker --config="$DOCKER_CONF" push "${IMAGE}:qa"
        docker --config="$DOCKER_CONF" tag "${IMAGE}:${IMAGE_TAG}" "${IMAGE}:latest"
        docker --config="$DOCKER_CONF" push "${IMAGE}:latest"
    fi
}

buildAndDeploy "notifications-aggregator"
buildAndDeploy "notifications-backend"
buildAndDeploy "notifications-connector-drawer"
buildAndDeploy "notifications-connector-email"
buildAndDeploy "notifications-connector-google-chat"
buildAndDeploy "notifications-connector-microsoft-teams"
buildAndDeploy "notifications-connector-servicenow"
buildAndDeploy "notifications-connector-slack"
buildAndDeploy "notifications-connector-splunk"
buildAndDeploy "notifications-connector-webhook"
buildAndDeploy "notifications-engine"
buildAndDeploy "notifications-recipients-resolver"
