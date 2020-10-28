ALTER TABLE public.notification_history
    ADD COLUMN event_id character varying;

INSERT INTO public.applications(name, description)
	VALUES ('Policies', 'Policies application')
	RETURNING id INTO policies_id;

INSERT INTO public.event_type(
	name, description)
	VALUES ('All', 'All triggers from Policies')
	RETURNING id INTO event_all_id;

INSERT INTO public.application_event_type(application_id, event_type_id)
	VALUES (policies_id, event_all_id);
