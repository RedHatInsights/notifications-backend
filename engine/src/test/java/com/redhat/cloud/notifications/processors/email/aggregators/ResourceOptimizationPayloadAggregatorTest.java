package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.ingress.Context;
import com.redhat.cloud.notifications.ingress.Event;
import com.redhat.cloud.notifications.ingress.Metadata;
import com.redhat.cloud.notifications.ingress.Payload;
import com.redhat.cloud.notifications.models.EmailAggregation;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.AGGREGATED_DATA;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.STATE;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.STATES;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.SYSTEMS_TRIGGERED;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.SYSTEMS_WITH_SUGGESTIONS;
import static com.redhat.cloud.notifications.processors.email.aggregators.ResourceOptimizationPayloadAggregator.SYSTEM_COUNT;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceOptimizationPayloadAggregatorTest {

    private static final String IDLING = "IDLING";
    private static final String UNDER_PRESSURE = "UNDER_PRESSURE";
    private static final String UNKNOWN = "UNKNOWN";
    private static final String INVENTORY_ID_1 = UUID.randomUUID().toString();
    private static final String INVENTORY_ID_2 = UUID.randomUUID().toString();
    private static final String INVENTORY_ID_3 = UUID.randomUUID().toString();
    private static final String INVENTORY_ID_4 = UUID.randomUUID().toString();
    private static final String INVENTORY_ID_5 = UUID.randomUUID().toString();

    @Test
    void testAggregate() {

        ResourceOptimizationPayloadAggregator aggregator = new ResourceOptimizationPayloadAggregator();
        aggregator.getContext().put("start_time", LocalDateTime.now().toString());
        aggregator.getContext().put("end_time", LocalDateTime.now().plusMinutes(1L).toString());
        aggregator.aggregate(buildEmailAggregation(101, INVENTORY_ID_1, IDLING));
        aggregator.aggregate(buildEmailAggregation(103, INVENTORY_ID_1, UNDER_PRESSURE));
        aggregator.aggregate(buildEmailAggregation(78, INVENTORY_ID_2, IDLING));
        aggregator.aggregate(buildEmailAggregation(48, INVENTORY_ID_2, UNKNOWN));
        aggregator.aggregate(buildEmailAggregation(121, INVENTORY_ID_2, IDLING));
        aggregator.aggregate(buildEmailAggregation(220, INVENTORY_ID_3, UNDER_PRESSURE));
        aggregator.aggregate(buildEmailAggregation(12, INVENTORY_ID_3, UNKNOWN));
        aggregator.aggregate(buildEmailAggregation(134, INVENTORY_ID_4, UNDER_PRESSURE));
        aggregator.aggregate(buildEmailAggregation(79, INVENTORY_ID_5, UNKNOWN));

        JsonObject aggregatedData = JsonObject.mapFrom(aggregator.getContext().get(AGGREGATED_DATA));
        assertEquals(79, aggregatedData.getInteger(SYSTEMS_WITH_SUGGESTIONS));
        assertEquals(5, aggregatedData.getInteger(SYSTEMS_TRIGGERED));
        assertEquals(3, aggregatedData.getJsonArray(STATES).size());

        JsonArray states = aggregatedData.getJsonArray(STATES);
        for (int i = 0; i < states.size(); i++) {
            JsonObject state = states.getJsonObject(i);
            switch (state.getString(STATE)) {
                case IDLING:
                    assertEquals(1, state.getInteger(SYSTEM_COUNT));
                    break;
                case UNDER_PRESSURE:
                    assertEquals(2, state.getInteger(SYSTEM_COUNT));
                    break;
                case UNKNOWN:
                    assertEquals(2, state.getInteger(SYSTEM_COUNT));
                    break;
                default:
                    // Do nothing.
                    break;
            }
        }
    }

    @Test
    void shouldCapTrackedSystemsAndTrackTotal() {
        int cap = 3;
        ResourceOptimizationPayloadAggregator cappedAggregator = new ResourceOptimizationPayloadAggregator(cap);

        int totalSystems = cap + 5;
        for (int i = 0; i < totalSystems; i++) {
            cappedAggregator.aggregate(buildEmailAggregation(100, UUID.randomUUID().toString(), IDLING));
        }

        JsonObject aggregatedData = JsonObject.mapFrom(cappedAggregator.getContext().get(AGGREGATED_DATA));
        assertEquals(cap, aggregatedData.getJsonArray(STATES).getJsonObject(0).getInteger(SYSTEM_COUNT),
            "tracked systems should be capped");
        assertEquals(totalSystems, aggregatedData.getInteger(SYSTEMS_TRIGGERED),
            "systems_triggered should reflect actual count");
    }

    @Test
    void shouldNotInflateCountForRepeatedUntrackedIds() {
        int cap = 2;
        ResourceOptimizationPayloadAggregator cappedAggregator = new ResourceOptimizationPayloadAggregator(cap);

        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        String id3 = UUID.randomUUID().toString();

        cappedAggregator.aggregate(buildEmailAggregation(10, id1, IDLING));
        cappedAggregator.aggregate(buildEmailAggregation(10, id2, IDLING));
        // id3 beyond cap
        cappedAggregator.aggregate(buildEmailAggregation(10, id3, IDLING));
        // repeat id3 — should NOT inflate count
        cappedAggregator.aggregate(buildEmailAggregation(10, id3, UNDER_PRESSURE));
        cappedAggregator.aggregate(buildEmailAggregation(10, id3, UNKNOWN));

        JsonObject aggregatedData = JsonObject.mapFrom(cappedAggregator.getContext().get(AGGREGATED_DATA));
        assertEquals(3, aggregatedData.getInteger(SYSTEMS_TRIGGERED),
            "repeated untracked ID should not inflate systems_triggered");
    }

    @Test
    void shouldStillUpdateExistingEntriesBeyondCap() {
        int cap = 2;
        ResourceOptimizationPayloadAggregator cappedAggregator = new ResourceOptimizationPayloadAggregator(cap);

        String id1 = UUID.randomUUID().toString();
        String id2 = UUID.randomUUID().toString();
        String id3 = UUID.randomUUID().toString();

        cappedAggregator.aggregate(buildEmailAggregation(10, id1, IDLING));
        cappedAggregator.aggregate(buildEmailAggregation(10, id2, IDLING));
        // id3 beyond cap — tracked in total but not in map
        cappedAggregator.aggregate(buildEmailAggregation(10, id3, IDLING));
        // update id1 state (already in map, should update)
        cappedAggregator.aggregate(buildEmailAggregation(10, id1, UNDER_PRESSURE));

        JsonObject aggregatedData = JsonObject.mapFrom(cappedAggregator.getContext().get(AGGREGATED_DATA));
        assertEquals(3, aggregatedData.getInteger(SYSTEMS_TRIGGERED));

        JsonArray states = aggregatedData.getJsonArray(STATES);
        for (int i = 0; i < states.size(); i++) {
            JsonObject state = states.getJsonObject(i);
            if (IDLING.equals(state.getString(STATE))) {
                assertEquals(1, state.getInteger(SYSTEM_COUNT));
            } else if (UNDER_PRESSURE.equals(state.getString(STATE))) {
                assertEquals(1, state.getInteger(SYSTEM_COUNT));
            }
        }
    }

    private static EmailAggregation buildEmailAggregation(int systemsWithSuggestions, String inventoryId, String currentState) {

        Action action = buildAction(systemsWithSuggestions, inventoryId, currentState);

        EmailAggregation aggregation = new EmailAggregation();
        aggregation.setBundleName("rhel");
        aggregation.setApplicationName("resource-optimization");
        aggregation.setOrgId(DEFAULT_ORG_ID);
        aggregation.setPayload(TestHelpers.wrapActionToJsonObject(action));
        return aggregation;
    }

    private static Action buildAction(int systemsWithSuggestions, String inventoryId, String currentState) {

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
                                .withAdditionalProperty("inventory_id", inventoryId)
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
