package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import com.redhat.cloud.notifications.transformers.BaseTransformer;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.AGGREGATED_DATA;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.RULES_TRIGGERED;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.STATE;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.STATES;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.SYSTEMS_WITH_SUGGESTIONS;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.SYSTEM_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceOptimizationPayloadAggregatorTest {

    private static final String IDLING = "IDLING";
    private static final String UNDER_PRESSURE = "UNDER_PRESSURE";
    private static final String UNKNOWN = "UNKNOWN";

    @Test
    void testAggregate() {

        ResourceOptimizationPayloadAggregator aggregator = new ResourceOptimizationPayloadAggregator();
        aggregator.getContext().put("start_time", LocalDateTime.now().toString());
        aggregator.getContext().put("end_time", LocalDateTime.now().plusMinutes(1L).toString());
        aggregator.aggregate(buildEmailAggregation(101, IDLING));
        aggregator.aggregate(buildEmailAggregation(78, IDLING));
        aggregator.aggregate(buildEmailAggregation(134, UNDER_PRESSURE));
        aggregator.aggregate(buildEmailAggregation(103, UNDER_PRESSURE));
        aggregator.aggregate(buildEmailAggregation(220, UNDER_PRESSURE));
        aggregator.aggregate(buildEmailAggregation(12, UNKNOWN));
        aggregator.aggregate(buildEmailAggregation(48, UNKNOWN));
        aggregator.aggregate(buildEmailAggregation(121, UNKNOWN));
        aggregator.aggregate(buildEmailAggregation(79, UNKNOWN));

        JsonObject aggregatedData = JsonObject.mapFrom(aggregator.getContext().get(AGGREGATED_DATA));
        assertEquals(79, aggregatedData.getInteger(SYSTEMS_WITH_SUGGESTIONS));
        assertEquals(9, aggregatedData.getInteger(RULES_TRIGGERED));
        assertEquals(3, aggregatedData.getJsonArray(STATES).size());
        assertEquals(IDLING, aggregatedData.getJsonArray(STATES).getJsonObject(0).getString(STATE));
        assertEquals(2, aggregatedData.getJsonArray(STATES).getJsonObject(0).getInteger(SYSTEM_COUNT));
        assertEquals(UNDER_PRESSURE, aggregatedData.getJsonArray(STATES).getJsonObject(1).getString(STATE));
        assertEquals(3, aggregatedData.getJsonArray(STATES).getJsonObject(1).getInteger(SYSTEM_COUNT));
        assertEquals(UNKNOWN, aggregatedData.getJsonArray(STATES).getJsonObject(2).getString(STATE));
        assertEquals(4, aggregatedData.getJsonArray(STATES).getJsonObject(2).getInteger(SYSTEM_COUNT));
    }

    private static EmailAggregation buildEmailAggregation(int systemsWithSuggestions, String currentState) {

        Action action = buildAction(systemsWithSuggestions, currentState);

        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName("rhel");
        aggregation.setApplicationName("resource-optimization");
        aggregation.setOrgId(DEFAULT_ORG_ID);
        aggregation.setPayload(new BaseTransformer().transform(action));
        return aggregation;
    }

    private static Action buildAction(int systemsWithSuggestions, String currentState) {

        return new Action.ActionBuilder()
                .withBundle("rhel")
                .withApplication("resource-optimization")
                .withEventType("new-suggestion")
                .withOrgId(DEFAULT_ORG_ID)
                .withTimestamp(LocalDateTime.now())
                .withContext(new Context.ContextBuilder()
                        .withAdditionalProperty("event_name", "New suggestion")
                        .withAdditionalProperty("systems_with_suggestions", systemsWithSuggestions)
                        .build()
                )
                .withEvents(List.of(new Event.EventBuilder()
                        .withMetadata(new Metadata.MetadataBuilder().build())
                        .withPayload(new Payload.PayloadBuilder()
                                .withAdditionalProperty("display_name", "ros-stage-sytem")
                                .withAdditionalProperty("inventory_id", "80f7e57d-a16a-4189-82af-1d68a747c8b3")
                                .withAdditionalProperty("message", "80f7e57d-a16a-4189-82af-1d68a747c8b3 has a new suggestion.")
                                .withAdditionalProperty("previous_state", "IDLING")
                                .withAdditionalProperty("current_state", currentState)
                                .build()
                        )
                        .build()
                ))
                .build();
    }
}
