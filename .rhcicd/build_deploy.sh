#!/bin/bash

set -exv

if [[ -z "$QUAY_USER" || -z "$QUAY_TOKEN" ]]; then
    echo "QUAY_USER and QUAY_TOKEN must be set"
    exit 1
fi

DOCKER_CONF="$PWD/.docker"
mkdir -p "$DOCKER_CONF"

IMAGE_TAG=$(git rev-parse --short=7 HEAD)
SECURITY_COMPLIANCE_TAG="sc-$(date +%Y%m%d)"

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
buildAndDeploy "notifications-engine"
#buildAndDeploy "notifications-camel-demo-log"
