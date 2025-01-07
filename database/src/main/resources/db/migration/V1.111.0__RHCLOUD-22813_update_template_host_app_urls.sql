UPDATE template
SET data = '{#if data.context.display_name??}<{data.inventory_url}|{data.context.display_name}> triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} from {data.source.application.display_name} - {data.source.bundle.display_name}. <{data.application_url}|Open {data.source.application.display_name}>'
WHERE name = 'generic-slack';

UPDATE template
SET data = '{"text":"{#if data.context.display_name??}[{data.context.display_name}]({data.inventory_url}) triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} from {data.source.application.display_name} - {data.source.bundle.display_name}. [Open {data.source.application.display_name}]({data.application_url})"}'
WHERE name = 'generic-teams';

UPDATE template
SET data = '{"text":"{#if data.context.display_name??}<{data.inventory_url}|{data.context.display_name}> triggered {data.events.size()} event{#if data.events.size() > 1}s{/if}{#else}{data.events.size()} event{#if data.events.size() > 1}s{/if} triggered{/if} from {data.source.application.display_name} - {data.source.bundle.display_name}. <{data.application_url}|Open {data.source.application.display_name}>"}'
WHERE name = 'generic-google-chat';