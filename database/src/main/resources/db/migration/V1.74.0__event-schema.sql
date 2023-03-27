ALTER TABLE event_type ADD COLUMN fqn VARCHAR(150);
CREATE UNIQUE INDEX IX_event_type_fqn ON public.event_type(fqn);
