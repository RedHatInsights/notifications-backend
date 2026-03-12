#!/bin/bash

#
# Bash safety options:
#   - e is for exiting immediately when a command exits with a non-zero status.
#   - u is for treating unset variables as an error when substituting.
#   - x is for printing all the commands as they're executed.
#   - o pipefail is for taking into account the exit status of the commands that run on pipelines.
#
set -euxo pipefail

# Start Postgresql and init notifications database
su -l postgres -c /usr/pgsql-16/bin/initdb
su -l postgres -c "/usr/pgsql-16/bin/pg_ctl -D /var/lib/pgsql/16/data -l /tmp/pg_logfile start"
createdb -U postgres notifications

# Start Redis
redis-server --daemonize yes

# Start Redpanda (Kafka-compatible)
echo "Starting Redpanda..."
mkdir -p /tmp/redpanda/data

# Use rpk to start Redpanda in container mode
# Change Schema Registry and Pandaproxy to non-conflicting ports if port 8081/8082 are in use
nohup rpk redpanda start \
  --node-id 0 \
  --kafka-addr 0.0.0.0:9092 \
  --advertise-kafka-addr localhost:9092 \
  --schema-registry-addr 0.0.0.0:18081 \
  --pandaproxy-addr 0.0.0.0:18082 \
  --set redpanda.data_directory=/tmp/redpanda/data \
  --set redpanda.empty_seed_starts_cluster=true \
  --set redpanda.auto_create_topics_enabled=true \
  > /tmp/redpanda.log 2>&1 &

#
# On the master branch there is no need to give the pull request details.
#
if [ -n "${GIT_BRANCH:-}" ] && [ "${GIT_BRANCH}" == "origin/master" ]; then
  ./mvnw clean verify sonar:sonar \
    -Dsonar.host.url="${SONARQUBE_HOST_URL}" \
    -Dsonar.exclusions="**/*.sql" \
    -Dsonar.projectKey="com.redhat.console.notifications.backend" \
    -Dsonar.projectVersion="${COMMIT_SHORT}" \
    -Dsonar.sourceEncoding="UTF-8" \
    -Dsonar.token="${SONARQUBE_TOKEN}" \
    -Dquarkus.devservices.enabled=false \
    -Dquarkus.datasource.devservices.enabled=false \
    -Dquarkus.redis.devservices.enabled=false \
    -Dquarkus.redis.hosts=redis://localhost/ \
    --no-transfer-progress
else
  ./mvnw clean verify sonar:sonar \
    -Dsonar.host.url="${SONARQUBE_HOST_URL}" \
    -Dsonar.exclusions="**/*.sql" \
    -Dsonar.projectKey="com.redhat.console.notifications.backend" \
    -Dsonar.projectVersion="${COMMIT_SHORT}" \
    -Dsonar.pullrequest.base="master" \
    -Dsonar.pullrequest.branch="${GIT_BRANCH}" \
    -Dsonar.pullrequest.key="${GITHUB_PULL_REQUEST_ID}" \
    -Dsonar.sourceEncoding="UTF-8" \
    -Dsonar.token="${SONARQUBE_TOKEN}" \
    -Dquarkus.devservices.enabled=false \
    -Dquarkus.datasource.devservices.enabled=false \
    -Dquarkus.redis.devservices.enabled=false \
    -Dquarkus.redis.hosts=redis://localhost/ \
    --no-transfer-progress
fi
