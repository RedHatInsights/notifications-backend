#!/bin/bash

set -exv

/bin/bash build_deploy_backend.sh
/bin/bash build_deploy_aggregator.sh
