#!/bin/bash

# Clowder config
export APP_NAME="notifications"
export COMPONENT_NAME="notifications-engine"
export IMAGE="quay.io/cloudservices/notifications-engine"
export DEPLOY_TIMEOUT="900"

# IQE plugin config
export IQE_PLUGINS="notifications"
export IQE_MARKER_EXPRESSION="notification_smoke and api"
export IQE_FILTER_EXPRESSION=""
export IQE_CJI_TIMEOUT="30m"

# Bonfire init
CICD_URL=https://raw.githubusercontent.com/RedHatInsights/bonfire/master/cicd
curl -s $CICD_URL/bootstrap.sh > .cicd_bootstrap.sh && source .cicd_bootstrap.sh

# Build the image and push to Quay
export DOCKERFILE=docker/Dockerfile.notifications-engine.jvm
source $CICD_ROOT/build.sh

# Deploy on ephemeral
export COMPONENTS="notifications-engine"
# Chaining the notifications-backend PR check with the notifications-engine PR check is
# not possible by default because the same ephemeral namespace is reserved and released
# during the former and is no longer available during the latter. The following line
# works around that limitation and triggers a new namespace reservation for each PR check.
export JOB_NAME="notifications-engine"
source $CICD_ROOT/deploy_ephemeral_env.sh

# Run smoke tests with ClowdJobInvocation
export COMPONENT_NAME="notifications-backend" # IQE tests won't run without this "hack".
source $CICD_ROOT/cji_smoke_test.sh
