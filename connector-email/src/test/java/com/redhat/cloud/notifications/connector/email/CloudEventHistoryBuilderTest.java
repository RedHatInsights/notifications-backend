package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.models.HandledEmailExceptionDetails;
import com.redhat.cloud.notifications.connector.email.models.HandledEmailMessageDetails;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.ADDITIONAL_ERROR_DETAILS;
import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudEventHistoryBuilderTest {

    private final CloudEventHistoryBuilder builder = new CloudEventHistoryBuilder();

    @Test
    void testBuildSuccessWithPayloadId() {
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();
        details.totalRecipients = 42;
        details.payloadId = "test-payload-id";

        JsonObject data = builder.buildSuccess(details);

        assertEquals(42, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertEquals("test-payload-id", data.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY));
    }

    @Test
    void testBuildSuccessWithoutPayloadId() {
        HandledEmailMessageDetails details = new HandledEmailMessageDetails();
        details.totalRecipients = 10;
        details.payloadId = null;

        JsonObject data = builder.buildSuccess(details);

        assertEquals(10, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertFalse(data.containsKey(PayloadDetails.PAYLOAD_DETAILS_ID_KEY));
    }

    @Test
    void testBuildSuccessWithNonEmailDetails() {
        HandledMessageDetails genericDetails = new HandledMessageDetails("Ok");
        JsonObject data = builder.buildSuccess(genericDetails);
        assertTrue(data.isEmpty());
    }

    @Test
    void testBuildFailureWithAdditionalErrorDetails() {
        HandledEmailExceptionDetails details = new HandledEmailExceptionDetails(new HandledExceptionDetails("error"));
        details.additionalErrorDetails = "{\"message\":\"Internal Server Error\"}";

        JsonObject data = builder.buildFailure(details);

        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertTrue(data.getJsonObject("details").getValue(ADDITIONAL_ERROR_DETAILS) instanceof JsonObject);
        assertEquals("Internal Server Error",
            data.getJsonObject("details").getJsonObject(ADDITIONAL_ERROR_DETAILS).getString("message"));
    }

    @Test
    void testBuildFailureWithNonJsonErrorDetails() {
        HandledEmailExceptionDetails details = new HandledEmailExceptionDetails(new HandledExceptionDetails("error"));
        details.additionalErrorDetails = "plain text error";

        JsonObject data = builder.buildFailure(details);

        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertEquals("plain text error", data.getJsonObject("details").getString(ADDITIONAL_ERROR_DETAILS));
    }

    @Test
    void testBuildFailureWithoutAdditionalErrorDetails() {
        HandledEmailExceptionDetails details = new HandledEmailExceptionDetails(new HandledExceptionDetails("error"));
        details.additionalErrorDetails = null;

        JsonObject data = builder.buildFailure(details);

        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertNull(data.getJsonObject("details").getValue(ADDITIONAL_ERROR_DETAILS));
    }
}
