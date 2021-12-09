#!/usr/bin/env bash
# Sends a notification to the local kafka environment using kafkacat
which kafkacat > /dev/null || (echo "kafkacat command is not available. Install it before continuing" && exit 1)
which jq > /dev/null || (echo "jq command is not available. Install it before continuing" && exit 1)

if [ -z "$ACCOUNT_ID" ]; then
  echo "ACCOUNT_ID is not set";
  exit 1
fi


BUNDLE='rhel'
APP='policies'
EVENT_TYPE='policy-triggered'
TIMESTAMP=$(date --utc +%FT%TZ)

read -r -d '' EVENTS <<JSONDOC
[
  {
    "metadata": {},
    "payload": {
      "policy_id": "00001",
      "policy_name": "My policy",
      "policy_description": "My policy description",
      "policy_condition": "arch = \"x86_64\""
    }
  }
]
JSONDOC

read -r -d ''  CONTEXT <<JSONDOC
{
  "system_check_in": "$(date --utc +%FT%TZ)",
  "display_name": "My system",
  "tags": {}
}
JSONDOC

read -r -d '' PAYLOAD <<JSONDOC
{
  "version": "v1.1.0",
  "bundle": "${BUNDLE}",
  "application": "${APP}",
  "event_type": "${EVENT_TYPE}",
  "timestamp": "${TIMESTAMP}",
  "account_id": "${ACCOUNT_ID}",
  "events": $(echo "${EVENTS}" | jq 'map(.payload |= (.. | tostring))'),
  "context": $(echo "${CONTEXT}" | jq '. | tostring'),
  "recipients": []
}
JSONDOC

echo "Sending notification:"
echo "$PAYLOAD" | jq '.'
echo "$PAYLOAD" | jq --compact-output '.' | kafkacat -P -t platform.notifications.ingress -b localhost:9092
