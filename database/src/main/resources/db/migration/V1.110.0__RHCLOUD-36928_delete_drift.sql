DELETE FROM applications a WHERE a.name = 'drift' AND EXISTS (SELECT 1 FROM bundles b WHERE b.name = 'rhel' AND b.id = a.bundle_id);
