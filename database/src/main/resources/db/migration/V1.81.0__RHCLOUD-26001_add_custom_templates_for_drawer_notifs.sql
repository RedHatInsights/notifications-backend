ALTER TABLE integration_template
    ADD COLUMN event_type_id UUID,
    ADD constraint fk_generic_template_event_id FOREIGN KEY (event_type_id) REFERENCES event_type (id) ON DELETE CASCADE;
