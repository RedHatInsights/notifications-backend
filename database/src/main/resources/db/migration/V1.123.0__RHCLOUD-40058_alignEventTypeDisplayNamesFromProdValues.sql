-- Update display_name for all event types
UPDATE event_type SET display_name = 'Vulnerability with known exploit identified' WHERE name = 'any-cve-known-exploit';
UPDATE event_type SET display_name = 'Integration availability status changed' WHERE name = 'availability-status';
UPDATE event_type SET display_name = 'Stale cost management data' WHERE name = 'cm-operator-stale';
UPDATE event_type SET display_name = 'Custom default access group updated' WHERE name = 'custom-default-access-updated';
UPDATE event_type SET display_name = 'Detected malware' WHERE name = 'detected-malware';
UPDATE event_type SET display_name = 'Executed task completed' WHERE name = 'executed-task-completed';
UPDATE event_type SET display_name = 'Missing OpenShift cost model' WHERE name = 'missing-cost-model';
UPDATE event_type SET display_name = 'New vulnerability containing security rule' WHERE name = 'new-cve-security-rule';
UPDATE event_type SET display_name = 'New vulnerability with critical severity' WHERE name = 'new-cve-severity';
UPDATE event_type SET display_name = 'Subscription Bug Fixes' WHERE name = 'new-subscription-bugfix-errata';
UPDATE event_type SET display_name = 'Subscription Enhancements' WHERE name = 'new-subscription-enhancement-errata';
UPDATE event_type SET display_name = 'Subscription Security Updates' WHERE name = 'new-subscription-security-errata';
UPDATE event_type SET display_name = 'Default access group has changed to custom default access' WHERE name = 'platform-default-group-turned-into-custom';
UPDATE event_type SET display_name = 'Access requested' WHERE name = 'request-access';
UPDATE event_type SET display_name = 'New Red Hat role added to default access group' WHERE name = 'rh-new-role-added-to-default-access';
UPDATE event_type SET display_name = 'Red Hat role not in default access group updated' WHERE name = 'rh-non-platform-default-role-updated';
UPDATE event_type SET display_name = 'Red Hat role in default access group updated' WHERE name = 'rh-platform-default-role-updated';
UPDATE event_type SET display_name = 'Red Hat role removed from default access group' WHERE name = 'rh-role-removed-from-default-access';
