ALTER TABLE public.notification_history
    ADD COLUMN event_id character varying;

do $$
declare
   policies_id uuid;
   event_all_id integer;
begin
    INSERT INTO public.applications(name, description)
        VALUES ('Policies', 'Policies')
        RETURNING id INTO policies_id;

    INSERT INTO public.event_type(
        name, description)
        VALUES ('All', 'Matching policy')
        RETURNING id INTO event_all_id;

    INSERT INTO public.application_event_type(application_id, event_type_id)
        VALUES (policies_id, event_all_id);
end; $$;

DROP INDEX "IX_application_name";

CREATE UNIQUE INDEX "IX_application_name"
    ON public.applications USING btree
    (name ASC NULLS LAST);
