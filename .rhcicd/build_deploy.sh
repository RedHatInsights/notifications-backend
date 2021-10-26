#!/bin/bash

set -exv

/bin/bash .rhcicd/build_deploy_backend.sh
/bin/bash .rhcicd/build_deploy_aggregator.sh
/bin/bash .rhcicd/build_deploy_camel-demo-log.sh
