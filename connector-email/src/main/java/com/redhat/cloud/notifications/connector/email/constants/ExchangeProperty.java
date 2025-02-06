package com.redhat.cloud.notifications.connector.email.constants;

public class ExchangeProperty {

    /**
     * Email recipients initially included in the payload received by notifications-engine.
     * The subscriptions of these recipients are not checked.
     * They are simply added to the list of recipients retrieved from notifications-recipients-resolver.
     */
    public static final String EMAIL_RECIPIENTS = "email_recipients";
    /**
     * Holds the email's sender that will be specified for sending the email.
     */
    public static final String EMAIL_SENDER = "email_sender";
    /**
     * Holds the filtered users. It is used in order to avoid the set of
     * cached users from being modified.
     */
    public static final String FILTERED_USERS = "users_filtered";
    /**
     * Holds the gathered recipient settings both from the subscription
     * endpoints and the received event.
     */
    public static final String RECIPIENT_SETTINGS = "recipient_settings";
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
     * Holds a boolean indicating if the event type is subscribed by default.
     */
    public static final String SUBSCRIBED_BY_DEFAULT = "subscribedByDefault";
    /**
     * Holds the rendered body contents, ready to be sent in an email.
     */
    public static final String RENDERED_BODY = "rendered_body";
    /**
     * Holds the rendered subject contents, ready to be sent in an email.
     */
    public static final String RENDERED_SUBJECT = "rendered_subject";

    public static final String RECIPIENTS_SIZE = "recipientsSize";

    public static final String RECIPIENTS_AUTHORIZATION_CRITERION = "recipients_authorization_criterion";

    public static final String ADDITIONAL_ERROR_DETAILS = "additionalErrorDetails";

    public static final String USE_SIMPLIFIED_EMAIL_ROUTE = "use_simplified_email_route";
}
