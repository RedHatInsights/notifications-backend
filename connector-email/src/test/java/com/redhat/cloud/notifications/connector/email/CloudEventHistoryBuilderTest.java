package com.redhat.cloud.notifications.connector.email;

import com.redhat.cloud.notifications.connector.email.model.HandledEmailExceptionDetails;
import com.redhat.cloud.notifications.connector.email.model.HandledEmailMessageDetails;
import com.redhat.cloud.notifications.connector.email.payload.PayloadDetails;
import com.redhat.cloud.notifications.connector.v2.OutgoingCloudEventBuilder;
import com.redhat.cloud.notifications.connector.v2.http.models.HandledHttpExceptionDetails;
import com.redhat.cloud.notifications.connector.v2.models.HandledMessageDetails;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.v2.http.HttpErrorType.HTTP_5XX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class CloudEventHistoryBuilderTest {

    @Inject
    OutgoingCloudEventBuilder builder;

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
    void testBuildFailureWithResponseBody() {
        HandledEmailExceptionDetails details = new HandledEmailExceptionDetails(new HandledHttpExceptionDetails());
        details.httpErrorType = HTTP_5XX;
        details.responseBody = "{\"message\":\"Internal Server Error\"}";

        JsonObject data = builder.buildFailure(details);

        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertTrue(data.containsKey("error"));
    }

    @Test
    void testBuildFailureWithPayloadId() {
        HandledEmailExceptionDetails details = new HandledEmailExceptionDetails(new HandledHttpExceptionDetails());
        details.payloadId = "test-payload-123";

        JsonObject data = builder.buildFailure(details);

        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertEquals("test-payload-123", data.getString(PayloadDetails.PAYLOAD_DETAILS_ID_KEY));
    }

    @Test
    void testBuildFailureWithoutResponseBody() {
        HandledEmailExceptionDetails details = new HandledEmailExceptionDetails(new HandledHttpExceptionDetails());

        JsonObject data = builder.buildFailure(details);

        assertEquals(0, data.getJsonObject("details").getInteger(TOTAL_RECIPIENTS_KEY));
        assertFalse(data.containsKey("error"));
    }
}
