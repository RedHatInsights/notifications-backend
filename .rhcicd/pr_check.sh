#!/bin/bash

set -exv

# Bonfire init
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

# Build a temporary image and push it to Quay
function buildAndPushToQuay() {
    IMAGE_NAME=$1
    export IMAGE="quay.io/cloudservices/${IMAGE_NAME}"
    export DOCKERFILE="docker/Dockerfile.${IMAGE_NAME}.jvm"
    source $CICD_ROOT/build.sh
}

buildAndPushToQuay "notifications-backend"
buildAndPushToQuay "notifications-connector-drawer"
buildAndPushToQuay "notifications-connector-email"
buildAndPushToQuay "notifications-connector-google-chat"
buildAndPushToQuay "notifications-connector-microsoft-teams"
buildAndPushToQuay "notifications-connector-servicenow"
buildAndPushToQuay "notifications-connector-slack"
buildAndPushToQuay "notifications-connector-splunk"
buildAndPushToQuay "notifications-connector-webhook"
buildAndPushToQuay "notifications-engine"

# Deploy all images on ephemeral
export APP_NAME="notifications"
export COMPONENT_NAME="notifications-backend"
export IMAGE="quay.io/cloudservices/notifications-backend"
export DEPLOY_TIMEOUT="1200"
export EXTRA_DEPLOY_ARGS="--set-parameter notifications-engine/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-email/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-google-chat/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-microsoft-teams/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-servicenow/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-slack/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-splunk/IMAGE_TAG=${IMAGE_TAG} --set-parameter notifications-connector-webhook/IMAGE_TAG=${IMAGE_TAG} --no-remove-resources historical-system-profiles --no-remove-resources system-baseline"
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
