-- Add severity column to event table
ALTER TABLE event_type
    ADD COLUMN default_severity VARCHAR(20),
    ADD COLUMN available_severities jsonb default json_build_array();

-- Ansible
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='notify-customer-provision-success';

-- Sources
update event_type set default_severity='LOW', available_severities=json_build_array('LOW') where name='availability-status';

-- Integrations
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='general-communication';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='integration-disabled';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='integration-test';

-- User Access
update event_type set default_severity='MODERATE', available_severities=json_build_array('MODERATE') where name='custom-default-access-updated';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='custom-role-created';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='custom-role-deleted';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='custom-role-updated';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='group-created';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='group-deleted';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='group-updated';
update event_type set default_severity='MODERATE', available_severities=json_build_array('MODERATE') where name='platform-default-group-turned-into-custom';
update event_type set default_severity='MODERATE', available_severities=json_build_array('MODERATE') where name='request-access';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='rh-new-role-added-to-default-access';
update event_type set default_severity='LOW', available_severities=json_build_array('LOW') where name='rh-new-role-available';
update event_type set default_severity='MODERATE', available_severities=json_build_array('MODERATE') where name='rh-new-tam-request-created';
update event_type set default_severity='MODERATE', available_severities=json_build_array('MODERATE') where name='rh-non-platform-default-role-updated';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='rh-platform-default-role-updated';
update event_type set default_severity='MODERATE', available_severities=json_build_array('MODERATE') where name='rh-role-removed-from-default-access';

-- Notifications
update event_type set default_severity='LOW', available_severities=json_build_array('LOW') where name='aggregation';

-- rhel advisor + openShit advisor: both default severity are CRITICAL
update event_type set default_severity='CRITICAL', available_severities=json_build_array('CRITICAL') where name='new-recommendation';

-- cost management
update event_type set default_severity='MODERATE', available_severities=json_build_array('MODERATE') where name='cm-operator-stale';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='missing-cost-model';

-- Advisor
update event_type set default_severity='NONE', available_severities=json_build_array('CRITICAL', 'IMPORTANT', 'MODERATE', 'LOW', 'NONE') where name='deactivated-recommendation';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='resolved-recommendation';

-- Compliance
update event_type set default_severity='CRITICAL', available_severities=json_build_array('CRITICAL') where name='compliance-below-threshold';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='report-upload-failed';

-- Malware
update event_type set default_severity='CRITICAL', available_severities=json_build_array('CRITICAL') where name='malware-detection';

-- Vulnerability
update event_type set default_severity='CRITICAL', available_severities=json_build_array('CRITICAL') where name='any-cve-known-exploit';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='new-cve-cvss';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='new-cve-security-rule';
update event_type set default_severity='CRITICAL', available_severities=json_build_array('CRITICAL') where name='new-cve-severity';

-- Patch
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='new-advisory';

-- Resource Optimization
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='new-suggestion';

-- Inventory
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='new-system-registered';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='system-became-stale';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='system-deleted';
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='validation-error';

-- Tasks
update event_type set default_severity='NONE', available_severities=json_build_array('NONE') where name='executed-task-completed';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='job-failed';

-- Errata
update event_type set default_severity='CRITICAL', available_severities=json_build_array('CRITICAL') where name='new-subscription-bugfix-errata';
update event_type set default_severity='IMPORTANT', available_severities=json_build_array('IMPORTANT') where name='new-subscription-enhancement-errata';
update event_type set default_severity='CRITICAL', available_severities=json_build_array('CRITICAL', 'IMPORTANT', 'MODERATE', 'LOW') where name='new-subscription-security-errata';
