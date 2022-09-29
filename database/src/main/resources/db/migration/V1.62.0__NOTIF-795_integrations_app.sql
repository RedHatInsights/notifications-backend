UPDATE applications a
    SET name = 'integrations', display_name = 'Integrations'
    WHERE name = 'notifications' and EXISTS (
        SELECT 1 FROM bundles b
        WHERE b.name = 'console' AND b.id = a.bundle_id
    );
