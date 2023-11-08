package com.redhat.cloud.notifications.connector.email.constants;

public class ExchangeProperty {
    /**
     * Used to store and retrieve the current recipient settings being
     * processed.
     */
    public static final String CURRENT_RECIPIENT_SETTINGS = "current_recipient_settings";
    /**
     * Used to hold the number of elements returned in a particular users
     * retrieval page.
     */
    public static final String ELEMENTS_COUNT = "elements_count";
    /**
     * Holds the filtered users. It is used in order to avoid the set of
     * cached users from being modified.
     */
    public static final String FILTERED_USERS = "users_filtered";
    /**
     * Used to hold the received RBAC group's UUID.
     */
    public static final String GROUP_UUID = "group_uuid";
    /**
     * The limit property used to determine if we should keep fetching elements.
     */
    public static final String LIMIT = "limit";
    /**
     * The offset property which will be used to fetch multiple pages.
     */
    public static final String OFFSET = "offset";
    /**
     * Holds the gathered recipient settings both from the subscription
     * endpoints and the received event.
     */
    public static final String RECIPIENT_SETTINGS = "recipient_settings";
    /**
     * A flag to determine if we need to send the email individually per user.
     */
    public static final String SINGLE_EMAIL_PER_USER = "single_email_per_user";
    /**
     * Holds the list of usernames who subscribed to the event type
     * that triggered the notification.
     */
    public static final String SUBSCRIBERS = "subscribers";
    /**
     * Holds the list of usernames who unsubscribed from the event type
     * that triggered the notification.
     */
    public static final String UNSUBSCRIBERS = "unsubscribers";
    /**
     * Holds the rendered body contents, ready to be sent in an email.
     */
    public static final String RENDERED_BODY = "rendered_body";
    /**
     * Holds the rendered subject contents, ready to be sent in an email.
     */
    public static final String RENDERED_SUBJECT = "rendered_subject";
    /**
     * Represents the curated set of recipients that will end up receiving the
     * notification through email.
     */
    public static final String USERS = "users";
    /**
     * Email recipients initially included in the payload received by notifications-engine.
     * The subscriptions of these recipients are not checked.
     * They are simply added to the list of recipients retrieved from notifications-recipients-resolver.
     */
    public static final String EMAIL_RECIPIENTS = "email_recipients";
}
