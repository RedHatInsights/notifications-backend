package com.redhat.cloud.notifications.routers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redhat.cloud.notifications.Json;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.models.AggregationCommand;
import com.redhat.cloud.notifications.models.EmailAggregationKey;
import com.redhat.cloud.notifications.models.Environment;
import com.redhat.cloud.notifications.models.SubscriptionType;
import com.redhat.cloud.notifications.routers.dailydigest.TriggerDailyDigestRequest;
import com.redhat.cloud.notifications.utils.ActionParser;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySink;
import jakarta.enterprise.inject.Any;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import static com.redhat.cloud.notifications.Constants.API_INTERNAL;
import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class DailyDigestResourceTest {

    @InjectMock
    Environment environment;

    @Any
    @Inject
    InMemoryConnector inMemoryConnector;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    ActionParser actionParser;

    /**
     * Tests that the daily digest cannot be triggered in an environment that
     * isn't "local" or "stage".
     */
    @Test
    public void testTriggerDailyDigestNonStage() {
        // Simulate that we are in the prod environment.
        Mockito.when(this.environment.isLocal()).thenReturn(false);
        Mockito.when(this.environment.isStage()).thenReturn(false);

        final TriggerDailyDigestRequest triggerDailyDigestRequest = new TriggerDailyDigestRequest(
            "application-name",
            "bundle-name",
            "organization-id",
            null,
            null
        );

        // Call the endpoint under test.
        final String response = given()
            .basePath(API_INTERNAL)
            .when()
            .contentType(JSON)
            .body(Json.encode(triggerDailyDigestRequest))
            .post("/daily-digest/trigger")
            .then()
            .statusCode(400)
            .extract()
            .asString();

        Assertions.assertEquals("the daily digests can only be triggered in the local or stage environment", response, "unexpected error message received");
    }

    /**
     * Tests that when calling the "trigger daily digest" endpoint, a proper
     * Kafka message, with an aggregation command, is sent.
     * @throws IOException if the incoming message from Kafka cannot be
     * deserialized.
     */
    @Test
    void testTriggerDailyDigest() throws IOException {
        final String applicationName = "application-name";
        final String bundleName = "bundle-name";
        final String orgId = "org-id";
        final LocalDateTime end = LocalDateTime.now();
        final LocalDateTime start = end.minusDays(5);

        final TriggerDailyDigestRequest triggerDailyDigestRequest = new TriggerDailyDigestRequest(
            applicationName,
            bundleName,
            orgId,
            start,
            end
        );

        // Simulate that we are in the stage environment.
        Mockito.when(this.environment.isStage()).thenReturn(true);

        // Call the endpoint under test.
        given()
            .basePath(API_INTERNAL)
            .when()
            .contentType(ContentType.JSON)
            .body(Json.encode(triggerDailyDigestRequest))
            .post("/daily-digest/trigger")
            .then()
            .statusCode(204);

        // We should receive the aggregation triggered by the REST call.
        InMemorySink<String> aggregationsOut = this.inMemoryConnector.sink(DailyDigestResource.AGGREGATION_OUT_CHANNEL);

        // Make sure that the message was received before continuing.
        Awaitility.await().until(
            () -> aggregationsOut.received().size() == 1
        );

        final var aggregationCommands = aggregationsOut.received();

        final String kafkaAggregationCommandRaw = aggregationCommands.get(0).getPayload();

        // We use an object mapper instead of the "Json.decodeValue" because Jackson doesn't seem to like the
        // constructor the "AggregationCommand" has.
        final Action action = actionParser.fromJsonString(kafkaAggregationCommandRaw);
        Map<String, Object> map = action.getEvents().get(0).getPayload().getAdditionalProperties();

        final AggregationCommand aggregationCommand = objectMapper.convertValue(map, AggregationCommand.class);

        Assertions.assertEquals(start, aggregationCommand.getStart(), "unexpected start timestamp coming from the Kafka aggregation command");
        Assertions.assertEquals(end, aggregationCommand.getEnd(), "unexpected end timestamp coming from the Kafka aggregation command");
        Assertions.assertEquals(SubscriptionType.DAILY, aggregationCommand.getSubscriptionType(), "unexpected subscription type coming from the Kafka aggregation command");

        final EmailAggregationKey emailAggregationKey = aggregationCommand.getAggregationKey();

        Assertions.assertEquals(applicationName, emailAggregationKey.getApplication(), "unexpected application name coming from the Kafka aggregation command");
        Assertions.assertEquals(bundleName, emailAggregationKey.getBundle(), "unexpected bundle name coming from the Kafka aggregation command");
        Assertions.assertEquals(orgId, emailAggregationKey.getOrgId(), "unexpected organization id coming from the Kafka aggregation command");
    }
}
