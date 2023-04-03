ALTER TABLE template
    add column application_id UUID,
    add constraint fk_generic_template_app_id FOREIGN KEY (application_id) REFERENCES applications (id) ON DELETE CASCADE;
