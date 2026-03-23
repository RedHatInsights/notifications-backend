UPDATE applications SET display_name = 'Cloud integrations' where name = 'sources';
UPDATE event SET application_display_name = 'Cloud integrations' where application_id in (select id from applications where name='sources');
