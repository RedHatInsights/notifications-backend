UPDATE applications SET display_name = 'Content' where name = 'patch';
UPDATE event SET application_display_name = 'Content' where application_id in (select id from applications where name='patch');
