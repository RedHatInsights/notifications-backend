package com.redhat.cloud.notifications.models.event;

/**
 * Holds the constants used in the test events that are sent when the clients
 * want to test their endpoints. See
 * <a href="https://issues.redhat.com/browse/RHCLOUD-22470">RHCLOUD-22470</a>
 * for more information.
 */
public class TestEventConstants {
    /**
     * Regular action test data.
     */
    public static final String TEST_ACTION_CONTEXT_TEST_EVENT = "test-action-context-test-event";
    public static final boolean TEST_ACTION_CONTEXT_TEST_EVENT_VALUE = true;
    public static final String TEST_ACTION_CONTEXT_ENDPOINT_ID = "test-action-context-endpoint-id";
    public static final String TEST_ACTION_METADATA_KEY = "test-metadata-key";
    public static final String TEST_ACTION_METADATA_VALUE = "test-metadata-value";
    public static final String TEST_ACTION_PAYLOAD_KEY = "test-payload-key";
    public static final String TEST_ACTION_PAYLOAD_VALUE = "test-payload-value";
    public static final String TEST_ACTION_RECIPIENT = "test-recipient-1";
    public static final String TEST_ACTION_VERSION = "0.0.0";
    /**
     * The test action or event will be sent with a defined bundle, application and event types, that have been
     * specifically inserted via a migration into the database. Please check the migration "V1.70.0" to see more
     * details about this.
     */
    public static final String TEST_ACTION_BUNDLE = "test-actions-events-bundle";
    public static final String TEST_ACTION_APPLICATION = "test-actions-events-application";
    public static final String TEST_ACTION_EVENT_TYPE = "test-event-type";
}
