DO $$
DECLARE templateId UUID;
BEGIN

INSERT INTO template (id, name, description, data, created)
VALUES (gen_random_uuid(), 'generic-drawer', 'Default template for drawer', '{#if data.context.display_name??}{data.context.display_name} triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if}', NOW() AT TIME ZONE 'UTC')
RETURNING id INTO templateId;

INSERT INTO integration_template (id, integration_type, template_kind, the_template_id, created)
VALUES (gen_random_uuid(), 'drawer', 0, templateId, NOW() AT TIME ZONE 'UTC');

END $$
