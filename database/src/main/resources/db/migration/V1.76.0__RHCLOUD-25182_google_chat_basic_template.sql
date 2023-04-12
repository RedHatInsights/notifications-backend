DO $$
DECLARE templateId UUID;
BEGIN

INSERT INTO template (id, name, description, data, created)
VALUES (gen_random_uuid(), 'generic-google-chat', 'Default template for Google Chat', '{"text":"{#if data.context.display_name??}<{data.environment_url}/insights/inventory/{data.context.inventory_id}|{data.context.display_name}> triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} from {data.bundle}/{data.application}. <{data.environment_url}/insights/{data.application}|Open {data.application}>"}', NOW() AT TIME ZONE 'UTC')
RETURNING id INTO templateId;

INSERT INTO integration_template (id, integration_type, template_kind, the_template_id, created)
VALUES (gen_random_uuid(), 'google_chat', 0, templateId, NOW() AT TIME ZONE 'UTC');

END $$
