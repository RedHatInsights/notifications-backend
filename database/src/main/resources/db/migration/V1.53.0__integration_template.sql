create table integration_template (
    id UUID NOT NULL,
    application_id UUID,
    integration_type VARCHAR(50),
    template_kind int4,
    account_id VARCHAR(50),
    the_template_id UUID NOT NULL,
    created TIMESTAMP NOT NULL,
    updated TIMESTAMP,

    constraint pk_generic_template PRIMARY KEY (id),
    constraint fk_generic_template_app_id FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_generic_template_template_id FOREIGN KEY (the_template_id) REFERENCES template (id) ON DELETE CASCADE
);

DO $$
DECLARE the_id UUID;
BEGIN
insert into template (id, name, description, data, created)
values (public.gen_random_uuid(),
    'generic-slack',
    'default template for slack',
    'Hello from *Notifications* via _OpenBridge_ with {data.events.size()} event{#if data.events.size() > 1}s{/} from Application _{data.application}_ in Bundle _{data.bundle}_\n" +
                    "Events: {data.events} \n" +
                    "{#if data.context.size() > 0} Context is:\n" +
                    "{#each data.context}*{it.key}* -> _{it.value}_\n" +
                    "{/each}{/if}\n" +
                    "Brought to you by :redhat:\n',
    now() at time zone 'UTC')
returning id into the_id;


insert into integration_template (id, integration_type, template_kind, the_template_id, created)
values (public.gen_random_uuid(),
    'slack',
    0,
    the_id,
    now() at time zone 'UTC'
    );

END $$
