#!/bin/bash

set -exv

# Bonfire init
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

# Build the notifications-backend image and push it to Quay
export IMAGE="quay.io/cloudservices/notifications-backend"
export DOCKERFILE=docker/Dockerfile.notifications-backend.jvm
source $CICD_ROOT/build.sh

# Build the notifications-engine image and push it to Quay
export IMAGE="quay.io/cloudservices/notifications-engine"
export DOCKERFILE=docker/Dockerfile.notifications-engine.jvm
source $CICD_ROOT/build.sh

# Build the notifications-connector-google-chat image and push it to Quay
export IMAGE="quay.io/cloudservices/notifications-connector-google-chat"
export DOCKERFILE=docker/Dockerfile.notifications-connector-google-chat.jvm
source $CICD_ROOT/build.sh

# Build the notifications-connector-microsoft-teams image and push it to Quay
export IMAGE="quay.io/cloudservices/notifications-connector-microsoft-teams"
export DOCKERFILE=docker/Dockerfile.notifications-connector-microsoft-teams.jvm
source $CICD_ROOT/build.sh

# Build the notifications-connector-servicenow image and push it to Quay
export IMAGE="quay.io/cloudservices/notifications-connector-servicenow"
export DOCKERFILE=docker/Dockerfile.notifications-connector-servicenow.jvm
source $CICD_ROOT/build.sh

# Build the notifications-connector-slack image and push it to Quay
export IMAGE="quay.io/cloudservices/notifications-connector-slack"
export DOCKERFILE=docker/Dockerfile.notifications-connector-slack.jvm
source $CICD_ROOT/build.sh

# Build the notifications-connector-splunk image and push it to Quay
export IMAGE="quay.io/cloudservices/notifications-connector-splunk"
export DOCKERFILE=docker/Dockerfile.notifications-connector-splunk.jvm
source $CICD_ROOT/build.sh

# Deploy all images on ephemeral
export APP_NAME="notifications"
export COMPONENT_NAME="notifications-backend"
export IMAGE="quay.io/cloudservices/notifications-backend"
export DEPLOY_TIMEOUT="900"
export EXTRA_DEPLOY_ARGS="--set-parameter notifications-engine/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-google-chat/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-microsoft-teams/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-servicenow/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-slack/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-splunk/IMAGE_TAG=${IMAGE_TAG} --no-remove-resources historical-system-profiles --no-remove-resources system-baseline"
source $CICD_ROOT/deploy_ephemeral_env.sh

# Run IQE tests
export IQE_PLUGINS="notifications"
export IQE_MARKER_EXPRESSION="notification_smoke and api"
export IQE_FILTER_EXPRESSION=""
export IQE_CJI_TIMEOUT="30m"
source $CICD_ROOT/cji_smoke_test.sh

# Build additional images that won't be pushed to Quay
docker build . -f docker/Dockerfile.notifications-aggregator.jvm

# Until test results produce a junit XML file, create a dummy result file so Jenkins will pass
mkdir -p $WORKSPACE/artifacts
cat << EOF > ${WORKSPACE}/artifacts/junit-dummy.xml
<testsuite tests="1">
    <testcase classname="dummy" name="dummytest"/>
</testsuite>
EOF
