UPDATE
    endpoints
SET
    "name" = CONCAT("name", ' ', id)
WHERE
    id IN (
        SELECT
            id
        FROM (
                 SELECT
                     id,
                     "name",
                     COUNT(*) OVER (PARTITION BY "name", org_id) AS duplicated_count
                 FROM
                     endpoints
                 WHERE
                     org_id IS NOT NULL
             ) inner_results
        WHERE inner_results.duplicated_count > 1
    );


UPDATE
    behavior_group
SET
    display_name = CONCAT(display_name, ' ', id)
WHERE
    id IN (
        SELECT
            id
        FROM (
                 SELECT
                     id,
                     display_name,
                     org_id,
                     COUNT(*) OVER (PARTITION BY display_name, org_id) AS duplicated_count
                 FROM
                     behavior_group
                 WHERE
                     org_id IS NOT NULL
             ) inner_results
        WHERE inner_results.duplicated_count > 1
    );
