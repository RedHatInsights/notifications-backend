UPDATE email_subscriptions es1
SET user_id = LOWER(user_id)
WHERE user_id <> LOWER(user_id)
AND NOT EXISTS (
    SELECT 1
    FROM email_subscriptions es2
    WHERE es2.user_id = LOWER(es1.user_id)
    AND es2.org_id = es1.org_id
    AND es2.event_type_id = es1.event_type_id
    AND es2.subscription_type = es1.subscription_type
);

DELETE FROM email_subscriptions
WHERE user_id <> LOWER(user_id);
