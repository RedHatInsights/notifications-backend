DO $$
DECLARE templateId UUID;
BEGIN

INSERT INTO template (id, name, description, data, created)
VALUES (gen_random_uuid(), 'generic-teams', 'Default template for Microsoft Teams', '{"@type": "MessageCard", "@context": "http://schema.org/extensions", "summary": "Red Hat notification", "sections": [{"activityTitle": "Instant notification - {data.application} - {data.bundle}", "activitySubtitle": "{#if data.context.display_name??}The following host triggered events{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if}"{#if data.context.display_name??}, "facts": [{"name": "Host", "value": "[{data.context.display_name}]({data.environment_url}{#if data.bundle == "openshift" and data.application == "advisor"}/openshift/insights/advisor/clusters/{data.context.display_name}{#else}/insights/inventory/{#if data.context.inventory_id??}{data.context.inventory_id}{#else}?hostname_or_id={data.context.display_name}{/if}{/if})"}, {"name": "Events", "value": "{data.events.size()}"}]{/if}}], "potentialAction": [{"@type": "ActionCard", "name": "Open {data.application}", "actions": [{"@type": "OpenUri", "name": "Open {data.application}", "targets": [{"os": "default", "uri": "{data.environment_url}/{#if data.bundle == "application-services" and data.application == "rhosak"}application-services/streams{#else}{#if data.bundle == "openshift"}openshift/{/if}insights/{data.application}{/if}"}]}]}]}', NOW() AT TIME ZONE 'UTC')
RETURNING id INTO templateId;

INSERT INTO integration_template (id, integration_type, template_kind, the_template_id, created)
VALUES (gen_random_uuid(), 'teams', 0, templateId, NOW() AT TIME ZONE 'UTC');

END $$
