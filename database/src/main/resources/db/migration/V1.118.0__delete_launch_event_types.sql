-- Delete the launch-success and launch-failed event types from the image-builder application
-- following the decommissioning of the Launch/Provisioning service
DELETE FROM event_type
WHERE name IN ('launch-success', 'launch-failed')
AND application_id = (
    SELECT a.id
    FROM applications a
    INNER JOIN bundles b ON b.id = a.bundle_id
    WHERE a.name = 'image-builder' AND b.name = 'rhel'
);

