#!/bin/bash
# =============================================================================
# Notifications Gateway - Email Event Type Test Payloads
# =============================================================================
# Extracted from common-template unit tests.
# One sample curl command per event_type that can generate an email.
# Ordered by bundle / application / event_type.
#
# Usage:
#   export ORG_ID="18939404"
#   export SESSION_COOKIE="y-_NIVtRe-LmaOPDh3YCJ-PKicCkGb87VHLByhjoHWs"
#   export STAGE_GATEWAY_HOST="internal.cloud.stage.example.com"
#   export PROD_GATEWAY_HOST="internal.cloud.example.com"
#   export PROXY_URL="http://proxy.example.com:3128"
#   export ENV=stage          (default) or export ENV=prod
#   bash test-event-types.sh <event_type_name>
#   bash test-event-types.sh --list
#   bash test-event-types.sh --all   (sends ALL events — use with care)
# =============================================================================

set -euo pipefail

: "${ORG_ID:?Set ORG_ID before running (e.g. export ORG_ID=18939404)}"
: "${SESSION_COOKIE:?Set SESSION_COOKIE before running (e.g. export SESSION_COOKIE=xxx)}"
: "${STAGE_GATEWAY_HOST:?Set STAGE_GATEWAY_HOST before running (e.g. export STAGE_GATEWAY_HOST=internal.cloud.stage.example.com)}"
: "${PROD_GATEWAY_HOST:?Set PROD_GATEWAY_HOST before running (e.g. export PROD_GATEWAY_HOST=internal.cloud.example.com)}"
: "${PROXY_URL:?Set PROXY_URL before running (e.g. export PROXY_URL=http://proxy.example.com:3128)}"

ENV="${ENV:-stage}"
case "$ENV" in
    stage) GATEWAY_URL="https://${STAGE_GATEWAY_HOST}/api/notifications-gw/notifications" ;;
    prod)  GATEWAY_URL="https://${PROD_GATEWAY_HOST}/api/notifications-gw/notifications" ;;
    *)     echo "Error: ENV must be 'stage' or 'prod' (got '$ENV')"; exit 1 ;;
esac
echo "Target: $ENV ($GATEWAY_URL)"
echo ""
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S.000")

send_event() {
    local description="$1"
    local payload="$2"
    echo ">>> Sending: $description"
    curl --silent --show-error --location "$GATEWAY_URL" \
        --proxy "$PROXY_URL" \
        --header "Cookie: session=$SESSION_COOKIE" \
        --header "Content-Type: application/json" \
        --data "$payload" \
        -w "\n    HTTP status: %{http_code}\n" \
        -o /dev/null
    echo ""
}

# =============================================================================
# ANSIBLE-AUTOMATION-PLATFORM
# =============================================================================

# -----------------------------------------------------------------------------
# ansible-automation-platform / ansible-service-on-aws / notify-customer-provision-success
# -----------------------------------------------------------------------------
ansible_automation_platform_ansible_service_on_aws_notify_customer_provision_success() {
    send_event "ansible-automation-platform / ansible-service-on-aws / notify-customer-provision-success" '{
    "bundle": "ansible-automation-platform",
    "application": "ansible-service-on-aws",
    "event_type": "notify-customer-provision-success",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "env_name": "cus-test-env",
        "bitwarden_url": "https://www.example.com"
    },
    "events": []
}'
}

# =============================================================================
# CONSOLE
# =============================================================================

# -----------------------------------------------------------------------------
# console / integrations / integration-disabled
# -----------------------------------------------------------------------------
console_integrations_integration_disabled() {
    send_event "console / integrations / integration-disabled" '{
    "bundle": "console",
    "application": "integrations",
    "event_type": "integration-disabled",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "error_type": "HTTP_4XX",
        "error_details": "",
        "endpoint_id": "c8a30166-526a-4e6c-b0c7-d24ec35279d3",
        "endpoint_name": "Unreliable integration",
        "endpoint_category": "Communications",
        "errors_count": 1,
        "status_code": 401
    },
    "events": [
        {
            "metadata": {},
            "payload": {}
        }
    ],
    "recipients": [
        {
            "only_admins": true,
            "ignore_user_preferences": true
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / integrations / general-communication
# -----------------------------------------------------------------------------
console_integrations_general_communication() {
    send_event "console / integrations / general-communication" '{
    "bundle": "console",
    "application": "integrations",
    "event_type": "general-communication",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "integration_category": "Reporting"
    },
    "events": [
        {
            "metadata": {
                "communication-description": "General communication about PagerDuty integrations switching to dynamic severity"
            },
            "payload": {
                "integration_names": ["Integration one", "Another integration", "Third integration"]
            }
        }
    ],
    "recipients": [
        {
            "only_admins": false,
            "ignore_user_preferences": true
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / rh-new-role-available
# -----------------------------------------------------------------------------
console_rbac_rh_new_role_available() {
    send_event "console / rbac / rh-new-role-available" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "rh-new-role-available",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / rh-platform-default-role-updated
# -----------------------------------------------------------------------------
console_rbac_rh_platform_default_role_updated() {
    send_event "console / rbac / rh-platform-default-role-updated" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "rh-platform-default-role-updated",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / rh-non-platform-default-role-updated
# -----------------------------------------------------------------------------
console_rbac_rh_non_platform_default_role_updated() {
    send_event "console / rbac / rh-non-platform-default-role-updated" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "rh-non-platform-default-role-updated",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / custom-role-created
# -----------------------------------------------------------------------------
console_rbac_custom_role_created() {
    send_event "console / rbac / custom-role-created" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "custom-role-created",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / custom-role-updated
# -----------------------------------------------------------------------------
console_rbac_custom_role_updated() {
    send_event "console / rbac / custom-role-updated" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "custom-role-updated",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / custom-role-deleted
# -----------------------------------------------------------------------------
console_rbac_custom_role_deleted() {
    send_event "console / rbac / custom-role-deleted" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "custom-role-deleted",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / rh-new-role-added-to-default-access
# -----------------------------------------------------------------------------
console_rbac_rh_new_role_added_to_default_access() {
    send_event "console / rbac / rh-new-role-added-to-default-access" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "rh-new-role-added-to-default-access",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / rh-role-removed-from-default-access
# -----------------------------------------------------------------------------
console_rbac_rh_role_removed_from_default_access() {
    send_event "console / rbac / rh-role-removed-from-default-access" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "rh-role-removed-from-default-access",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "removed",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / custom-default-access-updated
# -----------------------------------------------------------------------------
console_rbac_custom_default_access_updated() {
    send_event "console / rbac / custom-default-access-updated" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "custom-default-access-updated",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / group-created
# -----------------------------------------------------------------------------
console_rbac_group_created() {
    send_event "console / rbac / group-created" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "group-created",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / group-updated
# -----------------------------------------------------------------------------
console_rbac_group_updated() {
    send_event "console / rbac / group-updated" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "group-updated",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        },
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "username": "testUser1",
                "principal": "testUser1",
                "operation": "removed",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac6128a"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / group-deleted
# -----------------------------------------------------------------------------
console_rbac_group_deleted() {
    send_event "console / rbac / group-deleted" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "group-deleted",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / platform-default-group-turned-into-custom
# -----------------------------------------------------------------------------
console_rbac_platform_default_group_turned_into_custom() {
    send_event "console / rbac / platform-default-group-turned-into-custom" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "platform-default-group-turned-into-custom",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "role": {"name": "myRole", "uuid": "90d52d8b-614d-40f6-b073-1a88ee575f75"},
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / request-access
# -----------------------------------------------------------------------------
console_rbac_request_access() {
    send_event "console / rbac / request-access" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "request-access",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286",
                "url_path": "https://console.redhat.com/stuff",
                "user": {
                    "email": "testUser@example.com",
                    "request": "I want access to stuff"
                }
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / rbac / rh-new-tam-request-created
# -----------------------------------------------------------------------------
console_rbac_rh_new_tam_request_created() {
    send_event "console / rbac / rh-new-tam-request-created" '{
    "bundle": "console",
    "application": "rbac",
    "event_type": "rh-new-tam-request-created",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "name": "testRoleName",
                "username": "testUser1",
                "operation": "added",
                "uuid": "616ace4f-6024-4197-868a-2d0a2ac61286"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# console / scheduler / export-complete
# -----------------------------------------------------------------------------
console_scheduler_export_complete() {
    send_event "console / scheduler / export-complete" '{
    "bundle": "console",
    "application": "scheduler",
    "event_type": "export-complete",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "job_id": "job-12345",
        "job_name": "Test Export Job",
        "export_id": "export-67890",
        "run_id": "run-11111",
        "next_run_at": "2026-04-16T14:30:00"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# console / scheduler / job-failed
# -----------------------------------------------------------------------------
console_scheduler_job_failed() {
    send_event "console / scheduler / job-failed" '{
    "bundle": "console",
    "application": "scheduler",
    "event_type": "job-failed",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "job_id": "job-54321",
        "job_name": "Test Failed Job",
        "error_message": "Connection timeout"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# console / scheduler / job-failed-paused
# -----------------------------------------------------------------------------
console_scheduler_job_failed_paused() {
    send_event "console / scheduler / job-failed-paused" '{
    "bundle": "console",
    "application": "scheduler",
    "event_type": "job-failed-paused",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "job_id": "job-99999",
        "job_name": "Test Paused Job",
        "error_message": "Database connection failed"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# console / sources / availability-status
# -----------------------------------------------------------------------------
console_sources_availability_status() {
    send_event "console / sources / availability-status" '{
    "bundle": "console",
    "application": "sources",
    "event_type": "availability-status",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "source_id": 5,
        "resource_display_name": "test name 1",
        "previous_availability_status": "available",
        "current_availability_status": "unavailable",
        "source_name": "test source name 1"
    },
    "events": []
}'
}

# =============================================================================
# LIGHTWELL
# =============================================================================

# -----------------------------------------------------------------------------
# lightwell / lightwell / java-remediated
# -----------------------------------------------------------------------------
lightwell_lightwell_java_remediated() {
    send_event "lightwell / lightwell / java-remediated" '{
    "bundle": "lightwell",
    "application": "lightwell",
    "event_type": "java-remediated",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "package_name": "org.glassfish.jaxb:codemodel",
                "package_link": "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:codemodel",
                "releases": [
                    {
                        "release_names": [{"name": "4.0.4.rhlw003"}, {"name": "4.0.4.rhlw004"}],
                        "related_cve": [
                            {"cve": "CVE-2026-1234", "url": "https://console.redhat.com/api/lightwell/cves/CVE-2026-1234.json", "severity": "critical"},
                            {"cve": "CVE-2026-5678", "url": "https://console.redhat.com/api/lightwell/cves/CVE-2026-5678.json", "severity": "critical"}
                        ]
                    }
                ]
            }
        },
        {
            "metadata": {},
            "payload": {
                "package_name": "org.glassfish.jaxb:jaxb-core",
                "package_link": "https://console.redhat.com/lightwell/packages/org.glassfish.jaxb:jaxb-core",
                "releases": [
                    {
                        "release_names": [{"name": "4.0.4.rhlw003"}],
                        "related_cve": [
                            {"cve": "CVE-2026-1111", "url": "https://console.redhat.com/api/lightwell/cves/CVE-2026-1111.json", "severity": "important"}
                        ]
                    }
                ]
            }
        },
        {
            "metadata": {},
            "payload": {
                "package_name": "org.json:json",
                "package_link": "https://console.redhat.com/lightwell/packages/org.json:json",
                "releases": [
                    {
                        "release_names": [{"name": "20220320.0.0.rhlw-00002"}],
                        "related_cve": [
                            {"cve": "CVE-2026-0909", "url": "https://console.redhat.com/api/lightwell/cves/CVE-2026-0909.json", "severity": "critical"}
                        ]
                    }
                ]
            }
        }
    ]
}'
}

# =============================================================================
# OPENSHIFT
# =============================================================================

# -----------------------------------------------------------------------------
# openshift / advisor / new-recommendation
# -----------------------------------------------------------------------------
openshift_advisor_new_recommendation() {
    send_event "openshift / advisor / new-recommendation" '{
    "bundle": "openshift",
    "application": "advisor",
    "event_type": "new-recommendation",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "inventory_id": "host-01",
        "hostname": "my-host",
        "display_name": "My Host",
        "rhel_version": "8.3",
        "host_url": "this-is-my-host-url"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "rule_id": "rule-id-low-001",
                "rule_description": "nice rule with low risk",
                "total_risk": "1",
                "publish_date": "2020-08-03T15:22:42.199046",
                "report_url": "http://the-report-for-rule-id-low-001",
                "rule_url": "http://the-rule-id-low-001"
            }
        },
        {
            "metadata": {},
            "payload": {
                "rule_id": "rule-id-critical-001",
                "rule_description": "nice rule with critical risk",
                "total_risk": "4",
                "publish_date": "2020-08-03T15:22:42.199046",
                "report_url": "http://the-report-for-rule-id-critical-001",
                "rule_url": "http://the-rule-id-critical-001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-update
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_update() {
    send_event "openshift / cluster-manager / cluster-update" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-update",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "MOA",
                    "log_description": "Cluster upgrade to version 4.15.2 is in progress",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster upgrade scheduled",
                "title": "Upgrade scheduled"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-lifecycle
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_lifecycle() {
    send_event "openshift / cluster-manager / cluster-lifecycle" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-lifecycle",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster lifecycle event notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster lifecycle notification"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / customer-support
# -----------------------------------------------------------------------------
openshift_cluster_manager_customer_support() {
    send_event "openshift / cluster-manager / customer-support" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "customer-support",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "MOA",
                    "log_description": "Customer support notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Customer support update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / capacity-management
# -----------------------------------------------------------------------------
openshift_cluster_manager_capacity_management() {
    send_event "openshift / cluster-manager / capacity-management" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "capacity-management",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Capacity management notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Capacity management update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-access
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_access() {
    send_event "openshift / cluster-manager / cluster-access" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-access",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster access notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster access update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-add-on
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_add_on() {
    send_event "openshift / cluster-manager / cluster-add-on" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-add-on",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster add-on notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster add-on update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-configuration
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_configuration() {
    send_event "openshift / cluster-manager / cluster-configuration" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-configuration",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster configuration notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster configuration update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-networking
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_networking() {
    send_event "openshift / cluster-manager / cluster-networking" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-networking",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster networking notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster networking update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-ownership
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_ownership() {
    send_event "openshift / cluster-manager / cluster-ownership" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-ownership",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster ownership notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster ownership update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-scaling
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_scaling() {
    send_event "openshift / cluster-manager / cluster-scaling" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-scaling",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster scaling notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster scaling update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-security
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_security() {
    send_event "openshift / cluster-manager / cluster-security" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-security",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster security notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster security update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / cluster-subscription
# -----------------------------------------------------------------------------
openshift_cluster_manager_cluster_subscription() {
    send_event "openshift / cluster-manager / cluster-subscription" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "cluster-subscription",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "Cluster subscription notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "Cluster subscription update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cluster-manager / general-notification
# -----------------------------------------------------------------------------
openshift_cluster_manager_general_notification() {
    send_event "openshift / cluster-manager / general-notification" '{
    "bundle": "openshift",
    "application": "cluster-manager",
    "event_type": "general-notification",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2021-07-13T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "global_vars": {
                    "cluster_display_name": "My Production Cluster",
                    "subscription_id": "2XqNHRdLNEAzshh7MkkOql6fx6I",
                    "subscription_plan": "OSD",
                    "log_description": "General cluster notification",
                    "internal_cluster_id": "abc123def456"
                },
                "subject": "General notification"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / cost-management / missing-cost-model
# -----------------------------------------------------------------------------
openshift_cost_management_missing_cost_model() {
    send_event "openshift / cost-management / missing-cost-model" '{
    "bundle": "openshift",
    "application": "cost-management",
    "event_type": "missing-cost-model",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046",
        "source_name": "Dummy source name",
        "source_id": "12345",
        "cost_model_name": "Sample model",
        "cost_model_id": "4540543DGE"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# openshift / cost-management / cost-model-create
# -----------------------------------------------------------------------------
openshift_cost_management_cost_model_create() {
    send_event "openshift / cost-management / cost-model-create" '{
    "bundle": "openshift",
    "application": "cost-management",
    "event_type": "cost-model-create",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046",
        "source_name": "Dummy source name",
        "source_id": "12345",
        "cost_model_name": "Sample model",
        "cost_model_id": "4540543DGE"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# openshift / cost-management / cost-model-update
# -----------------------------------------------------------------------------
openshift_cost_management_cost_model_update() {
    send_event "openshift / cost-management / cost-model-update" '{
    "bundle": "openshift",
    "application": "cost-management",
    "event_type": "cost-model-update",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046",
        "source_name": "Dummy source name",
        "source_id": "12345",
        "cost_model_name": "Sample model",
        "cost_model_id": "4540543DGE"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# openshift / cost-management / cost-model-remove
# -----------------------------------------------------------------------------
openshift_cost_management_cost_model_remove() {
    send_event "openshift / cost-management / cost-model-remove" '{
    "bundle": "openshift",
    "application": "cost-management",
    "event_type": "cost-model-remove",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046",
        "source_name": "Dummy source name",
        "source_id": "12345",
        "cost_model_name": "Sample model",
        "cost_model_id": "4540543DGE"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# openshift / cost-management / cm-operator-stale
# -----------------------------------------------------------------------------
openshift_cost_management_cm_operator_stale() {
    send_event "openshift / cost-management / cm-operator-stale" '{
    "bundle": "openshift",
    "application": "cost-management",
    "event_type": "cm-operator-stale",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046",
        "source_name": "Dummy source name",
        "source_id": "12345",
        "cost_model_name": "Sample model",
        "cost_model_id": "4540543DGE"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# openshift / cost-management / cm-operator-data-processed
# -----------------------------------------------------------------------------
openshift_cost_management_cm_operator_data_processed() {
    send_event "openshift / cost-management / cm-operator-data-processed" '{
    "bundle": "openshift",
    "application": "cost-management",
    "event_type": "cm-operator-data-processed",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046",
        "source_name": "Dummy source name",
        "source_id": "12345",
        "cost_model_name": "Sample model",
        "cost_model_id": "4540543DGE"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# openshift / cost-management / cm-operator-data-received
# -----------------------------------------------------------------------------
openshift_cost_management_cm_operator_data_received() {
    send_event "openshift / cost-management / cm-operator-data-received" '{
    "bundle": "openshift",
    "application": "cost-management",
    "event_type": "cm-operator-data-received",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046",
        "source_name": "Dummy source name",
        "source_id": "12345",
        "cost_model_name": "Sample model",
        "cost_model_id": "4540543DGE"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# openshift / migration-advisor / partnership-request
# -----------------------------------------------------------------------------
openshift_migration_advisor_partnership_request() {
    send_event "openshift / migration-advisor / partnership-request" '{
    "bundle": "openshift",
    "application": "migration-advisor",
    "event_type": "partnership-request",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {}
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / migration-advisor / partnership-response
# -----------------------------------------------------------------------------
openshift_migration_advisor_partnership_response() {
    send_event "openshift / migration-advisor / partnership-response" '{
    "bundle": "openshift",
    "application": "migration-advisor",
    "event_type": "partnership-response",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "decision": "Accepted"
    },
    "events": [
        {
            "metadata": {},
            "payload": {}
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / migration-advisor / assessment-shared
# -----------------------------------------------------------------------------
openshift_migration_advisor_assessment_shared() {
    send_event "openshift / migration-advisor / assessment-shared" '{
    "bundle": "openshift",
    "application": "migration-advisor",
    "event_type": "assessment-shared",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "assessment_id": "assessment-123"
    },
    "events": [
        {
            "metadata": {},
            "payload": {}
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# openshift / migration-advisor / assessment-created
# -----------------------------------------------------------------------------
openshift_migration_advisor_assessment_created() {
    send_event "openshift / migration-advisor / assessment-created" '{
    "bundle": "openshift",
    "application": "migration-advisor",
    "event_type": "assessment-created",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "assessment_id": "assessment-456"
    },
    "events": [
        {
            "metadata": {},
            "payload": {}
        }
    ]
}'
}

# =============================================================================
# RHEL
# =============================================================================

# -----------------------------------------------------------------------------
# rhel / advisor / new-recommendation
# -----------------------------------------------------------------------------
rhel_advisor_new_recommendation() {
    send_event "rhel / advisor / new-recommendation" '{
    "bundle": "rhel",
    "application": "advisor",
    "event_type": "new-recommendation",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "inventory_id": "host-01",
        "hostname": "my-host",
        "display_name": "My Host",
        "rhel_version": "8.3",
        "host_url": "this-is-my-host-url"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "rule_id": "rule-id-low-001",
                "rule_description": "nice rule with low risk",
                "total_risk": "1",
                "publish_date": "2020-08-03T15:22:42.199046",
                "report_url": "http://the-report-for-rule-id-low-001",
                "rule_url": "http://the-rule-id-low-001"
            }
        },
        {
            "metadata": {},
            "payload": {
                "rule_id": "rule-id-critical-001",
                "rule_description": "nice rule with critical risk",
                "total_risk": "4",
                "publish_date": "2020-08-03T15:22:42.199046",
                "report_url": "http://the-report-for-rule-id-critical-001",
                "rule_url": "http://the-rule-id-critical-001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / advisor / deactivated-recommendation
# -----------------------------------------------------------------------------
rhel_advisor_deactivated_recommendation() {
    send_event "rhel / advisor / deactivated-recommendation" '{
    "bundle": "rhel",
    "application": "advisor",
    "event_type": "deactivated-recommendation",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "rule_id": "retire-rule1",
                "rule_description": "Rule being deactivated for retirement",
                "total_risk": 1,
                "affected_systems": 1,
                "deactivation_reason": "Retirement"
            }
        },
        {
            "metadata": {},
            "payload": {
                "rule_id": "enhance-rule2",
                "rule_description": "Rule being deactivated for enhancement",
                "total_risk": 2,
                "affected_systems": 2,
                "deactivation_reason": "Enhancement"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / advisor / resolved-recommendation
# -----------------------------------------------------------------------------
rhel_advisor_resolved_recommendation() {
    send_event "rhel / advisor / resolved-recommendation" '{
    "bundle": "rhel",
    "application": "advisor",
    "event_type": "resolved-recommendation",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "inventory_id": "host-01",
        "hostname": "my-host",
        "display_name": "My Host",
        "rhel_version": "8.3",
        "host_url": "this-is-my-host-url"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "rule_id": "rule-id-low-001",
                "rule_description": "nice rule with low risk",
                "total_risk": "1",
                "publish_date": "2020-08-03T15:22:42.199046",
                "report_url": "http://the-report-for-rule-id-low-001",
                "rule_url": "http://the-rule-id-low-001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / compliance / compliance-below-threshold
# -----------------------------------------------------------------------------
rhel_compliance_compliance_below_threshold() {
    send_event "rhel / compliance / compliance-below-threshold" '{
    "bundle": "rhel",
    "application": "compliance",
    "event_type": "compliance-below-threshold",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "host_id": "host-01",
                "host_name": "My test machine",
                "policy_id": "Policy id 1",
                "policy_name": "Tested name",
                "compliance_score": "20",
                "policy_threshold": "25",
                "request_id": "12345",
                "error": "Kernel panic (test)"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / compliance / report-upload-failed
# -----------------------------------------------------------------------------
rhel_compliance_report_upload_failed() {
    send_event "rhel / compliance / report-upload-failed" '{
    "bundle": "rhel",
    "application": "compliance",
    "event_type": "report-upload-failed",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "host_id": "host-01",
                "host_name": "My test machine",
                "policy_id": "Policy id 1",
                "policy_name": "Tested name",
                "compliance_score": "20",
                "policy_threshold": "25",
                "request_id": "12345",
                "error": "Kernel panic (test)"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / inventory / new-system-registered
# -----------------------------------------------------------------------------
rhel_inventory_new_system_registered() {
    send_event "rhel / inventory / new-system-registered" '{
    "bundle": "rhel",
    "application": "inventory",
    "event_type": "new-system-registered",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "new-host",
        "inventory_id": "05232955-721f-4e50-8a56-c8f3c45b17c3"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# rhel / inventory / system-became-stale
# -----------------------------------------------------------------------------
rhel_inventory_system_became_stale() {
    send_event "rhel / inventory / system-became-stale" '{
    "bundle": "rhel",
    "application": "inventory",
    "event_type": "system-became-stale",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "stale-host",
        "inventory_id": "d7646022-dcdc-44df-b1a8-f796d932d5a1"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# rhel / inventory / system-deleted
# -----------------------------------------------------------------------------
rhel_inventory_system_deleted() {
    send_event "rhel / inventory / system-deleted" '{
    "bundle": "rhel",
    "application": "inventory",
    "event_type": "system-deleted",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "deleted-host",
        "inventory_id": "75bb495a-3492-470e-b8c3-7ec45c813c08"
    },
    "events": []
}'
}

# -----------------------------------------------------------------------------
# rhel / inventory / validation-error
# -----------------------------------------------------------------------------
rhel_inventory_validation_error() {
    send_event "rhel / inventory / validation-error" '{
    "bundle": "rhel",
    "application": "inventory",
    "event_type": "validation-error",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "severity": "IMPORTANT",
    "context": {
        "event_name": "Host Validation Error"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "host_id": "host-01",
                "display_name": "random_name",
                "error": {
                    "code": "VE001",
                    "message": "error 1",
                    "stack_trace": "",
                    "severity": "error"
                }
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / life-cycle / retiring-lifecycle-monthly-report
# -----------------------------------------------------------------------------
rhel_lifecycle_retiring_lifecycle_monthly_report() {
    send_event "rhel / life-cycle / retiring-lifecycle-monthly-report" '{
    "bundle": "rhel",
    "application": "life-cycle",
    "event_type": "retiring-lifecycle-monthly-report",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "lifecycle": {
            "report_date": "15th Dec 2025"
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "rhel_retired": {"rhel_versions_count": 6, "systems_count": 5},
                "rhel_near_retirement": {"rhel_versions_count": 1, "systems_count": 3},
                "appstream_retired": {
                    "rhel8": {"count": 2, "systems_count": 5},
                    "rhel9": {"count": 2, "systems_count": 8}
                },
                "appstream_near_retirement": {
                    "rhel8": {"count": 5, "systems_count": 7},
                    "rhel9": {"count": 22, "systems_count": 6}
                }
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / malware-detection / detected-malware
# -----------------------------------------------------------------------------
rhel_malware_detection_detected_malware() {
    send_event "rhel / malware-detection / detected-malware" '{
    "bundle": "rhel",
    "application": "malware-detection",
    "event_type": "detected-malware",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2020-08-03T15:22:42.199046"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "host_id": "host-01",
                "host_name": "My test machine",
                "matched_rules": ["rule 1", "rule 2"],
                "matched_at": "2020-08-03T15:22:42.199046"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / patch / new-advisory
# -----------------------------------------------------------------------------
rhel_patch_new_advisory() {
    send_event "rhel / patch / new-advisory" '{
    "bundle": "rhel",
    "application": "patch",
    "event_type": "new-advisory",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2022-08-03T15:22:42.199046",
        "start_time": "2022-08-03T15:22:42.199046",
        "patch": {
            "Numerical": ["adv1", "adv2"],
            "Roman": ["advI", "advII", "advIII"],
            "Alpha": ["advA", "advB", "advC"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "advisory_name": "name 1",
                "synopsis": "synopsis 1"
            }
        },
        {
            "metadata": {},
            "payload": {
                "advisory_name": "name 2",
                "synopsis": "synopsis 2"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / roadmap / roadmap-monthly-report
# -----------------------------------------------------------------------------
rhel_roadmap_roadmap_monthly_report() {
    send_event "rhel / roadmap / roadmap-monthly-report" '{
    "bundle": "rhel",
    "application": "roadmap",
    "event_type": "roadmap-monthly-report",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "roadmap": {
            "report_date": "1st May 2026"
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "deprecation": {"count": 5},
                "change": {"count": 3},
                "addition": {"count": 4}
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / tasks / executed-task-completed
# -----------------------------------------------------------------------------
rhel_tasks_executed_task_completed() {
    send_event "rhel / tasks / executed-task-completed" '{
    "bundle": "rhel",
    "application": "tasks",
    "event_type": "executed-task-completed",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {},
    "events": [
        {
            "metadata": {},
            "payload": {
                "task_name": "RHEL pre-upgrade analysis utility",
                "executed_task_id": 9651,
                "status": "Completed"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / tasks / job-failed
# -----------------------------------------------------------------------------
rhel_tasks_job_failed() {
    send_event "rhel / tasks / job-failed" '{
    "bundle": "rhel",
    "application": "tasks",
    "event_type": "job-failed",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "task_name": "test_task",
        "task_slug": "leapp-preupgrade",
        "executed_task_id": 10750
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "system_uuid": "0c1fa20b-889b-469c-993d-775c06480cd8",
                "display_name": "iqe-jenkins-tasks-rhel-89-prod",
                "status": "TIMEOUT"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / any-cve-known-exploit
# -----------------------------------------------------------------------------
rhel_vulnerability_any_cve_known_exploit() {
    send_event "rhel / vulnerability / any-cve-known-exploit" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "any-cve-known-exploit",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / new-cve-severity
# -----------------------------------------------------------------------------
rhel_vulnerability_new_cve_severity() {
    send_event "rhel / vulnerability / new-cve-severity" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "new-cve-severity",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / new-cve-cvss
# -----------------------------------------------------------------------------
rhel_vulnerability_new_cve_cvss() {
    send_event "rhel / vulnerability / new-cve-cvss" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "new-cve-cvss",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / new-cve-security-rule
# -----------------------------------------------------------------------------
rhel_vulnerability_new_cve_security_rule() {
    send_event "rhel / vulnerability / new-cve-security-rule" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "new-cve-security-rule",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / new-cve-all
# -----------------------------------------------------------------------------
rhel_vulnerability_new_cve_all() {
    send_event "rhel / vulnerability / new-cve-all" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "new-cve-all",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / system-cve-known-exploit
# -----------------------------------------------------------------------------
rhel_vulnerability_system_cve_known_exploit() {
    send_event "rhel / vulnerability / system-cve-known-exploit" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "system-cve-known-exploit",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "test-system",
        "inventory_id": "b8125066-0db2-47df-b37a-09e71979269f",
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        },
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0002"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / system-cve-severity
# -----------------------------------------------------------------------------
rhel_vulnerability_system_cve_severity() {
    send_event "rhel / vulnerability / system-cve-severity" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "system-cve-severity",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "test-system",
        "inventory_id": "b8125066-0db2-47df-b37a-09e71979269f",
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        },
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0002"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / system-cve-cvss
# -----------------------------------------------------------------------------
rhel_vulnerability_system_cve_cvss() {
    send_event "rhel / vulnerability / system-cve-cvss" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "system-cve-cvss",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "test-system",
        "inventory_id": "b8125066-0db2-47df-b37a-09e71979269f",
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        },
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0002"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / system-cve-security-rule
# -----------------------------------------------------------------------------
rhel_vulnerability_system_cve_security_rule() {
    send_event "rhel / vulnerability / system-cve-security-rule" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "system-cve-security-rule",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "test-system",
        "inventory_id": "b8125066-0db2-47df-b37a-09e71979269f",
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        },
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0002"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# rhel / vulnerability / system-cve-all
# -----------------------------------------------------------------------------
rhel_vulnerability_system_cve_all() {
    send_event "rhel / vulnerability / system-cve-all" '{
    "bundle": "rhel",
    "application": "vulnerability",
    "event_type": "system-cve-all",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "display_name": "test-system",
        "inventory_id": "b8125066-0db2-47df-b37a-09e71979269f",
        "vulnerability": {
            "reported_cves": ["CVE-2024-0001", "CVE-2024-0002", "CVE-2024-0003"]
        }
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0001"
            }
        },
        {
            "metadata": {},
            "payload": {
                "reported_cve": "CVE-2024-0002"
            }
        }
    ]
}'
}

# =============================================================================
# SUBSCRIPTION-SERVICES
# =============================================================================

# -----------------------------------------------------------------------------
# subscription-services / errata-notifications / new-subscription-bugfix-errata
# -----------------------------------------------------------------------------
subscription_services_errata_new_subscription_bugfix_errata() {
    send_event "subscription-services / errata-notifications / new-subscription-bugfix-errata" '{
    "bundle": "subscription-services",
    "application": "errata-notifications",
    "event_type": "new-subscription-bugfix-errata",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2022-08-03T15:22:42.199046",
        "start_time": "2022-08-03T15:22:42.199046",
        "base_url": "https://access.redhat.com/errata/"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:2106",
                "severity": "Moderate",
                "synopsis": "Red Hat build of Quarkus 3.8.4 release"
            }
        },
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:3842",
                "severity": "Important",
                "synopsis": "c-ares security update"
            }
        },
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:3843",
                "severity": "Low",
                "synopsis": "cockpit security update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# subscription-services / errata-notifications / new-subscription-security-errata
# -----------------------------------------------------------------------------
subscription_services_errata_new_subscription_security_errata() {
    send_event "subscription-services / errata-notifications / new-subscription-security-errata" '{
    "bundle": "subscription-services",
    "application": "errata-notifications",
    "event_type": "new-subscription-security-errata",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2022-08-03T15:22:42.199046",
        "start_time": "2022-08-03T15:22:42.199046",
        "base_url": "https://access.redhat.com/errata/"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:2106",
                "severity": "Moderate",
                "synopsis": "Red Hat build of Quarkus 3.8.4 release"
            }
        },
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:3842",
                "severity": "Important",
                "synopsis": "c-ares security update"
            }
        },
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:3843",
                "severity": "Low",
                "synopsis": "cockpit security update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# subscription-services / errata-notifications / new-subscription-enhancement-errata
# -----------------------------------------------------------------------------
subscription_services_errata_new_subscription_enhancement_errata() {
    send_event "subscription-services / errata-notifications / new-subscription-enhancement-errata" '{
    "bundle": "subscription-services",
    "application": "errata-notifications",
    "event_type": "new-subscription-enhancement-errata",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "system_check_in": "2022-08-03T15:22:42.199046",
        "start_time": "2022-08-03T15:22:42.199046",
        "base_url": "https://access.redhat.com/errata/"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:2106",
                "severity": "Moderate",
                "synopsis": "Red Hat build of Quarkus 3.8.4 release"
            }
        },
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:3842",
                "severity": "Important",
                "synopsis": "c-ares security update"
            }
        },
        {
            "metadata": {},
            "payload": {
                "id": "RHSA-2024:3843",
                "severity": "Low",
                "synopsis": "cockpit security update"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# subscription-services / subscriptions / exceeded-utilization-threshold
# -----------------------------------------------------------------------------
subscription_services_subscriptions_exceeded_utilization_threshold() {
    send_event "subscription-services / subscriptions / exceeded-utilization-threshold" '{
    "bundle": "subscription-services",
    "application": "subscriptions",
    "event_type": "exceeded-utilization-threshold",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "product_id": "RHEL for x86",
        "metric_id": "sockets",
        "service_level": "Premium",
        "usage": "Production"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "utilization_percentage": "105"
            }
        }
    ]
}'
}

# -----------------------------------------------------------------------------
# subscription-services / subscriptions / exceeded-custom-utilization-threshold
# -----------------------------------------------------------------------------
subscription_services_subscriptions_exceeded_custom_utilization_threshold() {
    send_event "subscription-services / subscriptions / exceeded-custom-utilization-threshold" '{
    "bundle": "subscription-services",
    "application": "subscriptions",
    "event_type": "exceeded-custom-utilization-threshold",
    "timestamp": "'"$TIMESTAMP"'",
    "org_id": "'"$ORG_ID"'",
    "context": {
        "product_id": "RHEL for x86",
        "metric_id": "sockets",
        "service_level": "Premium",
        "usage": "Production"
    },
    "events": [
        {
            "metadata": {},
            "payload": {
                "utilization_percentage": "105"
            }
        }
    ]
}'
}

# =============================================================================
# Event registry and dispatcher
# =============================================================================

ALL_EVENTS=(
    # ansible-automation-platform / ansible-service-on-aws
    "ansible_automation_platform_ansible_service_on_aws_notify_customer_provision_success"
    # console / integrations
    "console_integrations_integration_disabled"
    "console_integrations_general_communication"
    # console / rbac
    "console_rbac_rh_new_role_available"
    "console_rbac_rh_platform_default_role_updated"
    "console_rbac_rh_non_platform_default_role_updated"
    "console_rbac_custom_role_created"
    "console_rbac_custom_role_updated"
    "console_rbac_custom_role_deleted"
    "console_rbac_rh_new_role_added_to_default_access"
    "console_rbac_rh_role_removed_from_default_access"
    "console_rbac_custom_default_access_updated"
    "console_rbac_group_created"
    "console_rbac_group_updated"
    "console_rbac_group_deleted"
    "console_rbac_platform_default_group_turned_into_custom"
    "console_rbac_request_access"
    "console_rbac_rh_new_tam_request_created"
    # console / scheduler
    "console_scheduler_export_complete"
    "console_scheduler_job_failed"
    "console_scheduler_job_failed_paused"
    # console / sources
    "console_sources_availability_status"
    # lightwell / lightwell
    "lightwell_lightwell_java_remediated"
    # openshift / advisor
    "openshift_advisor_new_recommendation"
    # openshift / cluster-manager
    "openshift_cluster_manager_cluster_update"
    "openshift_cluster_manager_cluster_lifecycle"
    "openshift_cluster_manager_customer_support"
    "openshift_cluster_manager_capacity_management"
    "openshift_cluster_manager_cluster_access"
    "openshift_cluster_manager_cluster_add_on"
    "openshift_cluster_manager_cluster_configuration"
    "openshift_cluster_manager_cluster_networking"
    "openshift_cluster_manager_cluster_ownership"
    "openshift_cluster_manager_cluster_scaling"
    "openshift_cluster_manager_cluster_security"
    "openshift_cluster_manager_cluster_subscription"
    "openshift_cluster_manager_general_notification"
    # openshift / cost-management
    "openshift_cost_management_missing_cost_model"
    "openshift_cost_management_cost_model_create"
    "openshift_cost_management_cost_model_update"
    "openshift_cost_management_cost_model_remove"
    "openshift_cost_management_cm_operator_stale"
    "openshift_cost_management_cm_operator_data_processed"
    "openshift_cost_management_cm_operator_data_received"
    # openshift / migration-advisor
    "openshift_migration_advisor_partnership_request"
    "openshift_migration_advisor_partnership_response"
    "openshift_migration_advisor_assessment_shared"
    "openshift_migration_advisor_assessment_created"
    # rhel / advisor
    "rhel_advisor_new_recommendation"
    "rhel_advisor_deactivated_recommendation"
    "rhel_advisor_resolved_recommendation"
    # rhel / compliance
    "rhel_compliance_compliance_below_threshold"
    "rhel_compliance_report_upload_failed"
    # rhel / inventory
    "rhel_inventory_new_system_registered"
    "rhel_inventory_system_became_stale"
    "rhel_inventory_system_deleted"
    "rhel_inventory_validation_error"
    # rhel / life-cycle
    "rhel_lifecycle_retiring_lifecycle_monthly_report"
    # rhel / malware-detection
    "rhel_malware_detection_detected_malware"
    # rhel / patch
    "rhel_patch_new_advisory"
    # rhel / roadmap
    "rhel_roadmap_roadmap_monthly_report"
    # rhel / tasks
    "rhel_tasks_executed_task_completed"
    "rhel_tasks_job_failed"
    # rhel / vulnerability
    "rhel_vulnerability_any_cve_known_exploit"
    "rhel_vulnerability_new_cve_severity"
    "rhel_vulnerability_new_cve_cvss"
    "rhel_vulnerability_new_cve_security_rule"
    "rhel_vulnerability_new_cve_all"
    "rhel_vulnerability_system_cve_known_exploit"
    "rhel_vulnerability_system_cve_severity"
    "rhel_vulnerability_system_cve_cvss"
    "rhel_vulnerability_system_cve_security_rule"
    "rhel_vulnerability_system_cve_all"
    # subscription-services / errata-notifications
    "subscription_services_errata_new_subscription_bugfix_errata"
    "subscription_services_errata_new_subscription_security_errata"
    "subscription_services_errata_new_subscription_enhancement_errata"
    # subscription-services / subscriptions
    "subscription_services_subscriptions_exceeded_utilization_threshold"
    "subscription_services_subscriptions_exceeded_custom_utilization_threshold"
)

list_events() {
    echo "Available event types (${#ALL_EVENTS[@]} total):"
    echo ""
    for fn in "${ALL_EVENTS[@]}"; do
        echo "  $fn"
    done
}

run_all() {
    echo "Sending all ${#ALL_EVENTS[@]} event types..."
    echo ""
    for fn in "${ALL_EVENTS[@]}"; do
        "$fn"
    done
    echo "Done. Sent ${#ALL_EVENTS[@]} events."
}

# Main dispatcher
case "${1:-}" in
    --list)
        list_events
        ;;
    --all)
        run_all
        ;;
    "")
        echo "Usage: $0 <function_name> | --list | --all"
        echo ""
        echo "Examples:"
        echo "  export ORG_ID=18939404"
        echo "  export SESSION_COOKIE=your-session-cookie"
        echo "  export STAGE_GATEWAY_HOST=internal.cloud.stage.example.com"
        echo "  export PROD_GATEWAY_HOST=internal.cloud.example.com"
        echo "  export PROXY_URL=http://proxy.example.com:3128"
        echo "  $0 --list"
        echo "  $0 rhel_advisor_new_recommendation"
        echo "  $0 --all"
        exit 1
        ;;
    *)
        if declare -f "$1" > /dev/null 2>&1; then
            "$1"
        else
            echo "Error: Unknown event type function '$1'"
            echo "Run '$0 --list' to see available functions."
            exit 1
        fi
        ;;
esac
