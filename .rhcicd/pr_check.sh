#!/bin/bash

set -exv

IMAGE="quay.io/cloudservices/notifications-backend"
IMAGE_TAG=$(git rev-parse --short=7 HEAD)

DOCKER_CONF="$PWD/.docker"
mkdir -p "$DOCKER_CONF"

docker --config="$DOCKER_CONF" build -t "${IMAGE}:${IMAGE_TAG}" . -f docker/Dockerfile-build.jvm