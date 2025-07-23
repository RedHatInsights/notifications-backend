package com.redhat.cloud.notifications.processors.email.aggregators;

import com.redhat.cloud.notifications.ErrataTestHelpers;
import com.redhat.cloud.notifications.TestHelpers;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;

import static com.redhat.cloud.notifications.TestConstants.DEFAULT_ORG_ID;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_BUGFIX;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_ENHANCEMENT;
import static com.redhat.cloud.notifications.processors.email.aggregators.ErrataEmailPayloadAggregator.EVENT_TYPE_SECURITY;
import static org.junit.jupiter.api.Assertions.*;

class ErrataEmailPayloadAggregatorTest {

    ErrataEmailPayloadAggregator aggregator;

    @BeforeEach
    void initTest() {
        aggregator = new ErrataEmailPayloadAggregator();
    }

    @Test
    void emptyAggregatorHasNoOrgId() {
        assertNull(aggregator.getOrgId(), "Empty aggregator has no orgId");
    }

    @Test
    void aggregatorTests() {
        aggregateEventType("", 1);
        assertEquals(DEFAULT_ORG_ID, aggregator.getOrgId());

        Map<String, Object> context = aggregator.getContext();
        JsonObject errata = JsonObject.mapFrom(context).getJsonObject("errata");

        assertEquals(0, errata.getJsonArray("new-subscription-bugfix-errata").size());
        assertEquals(0, errata.getJsonArray("new-subscription-enhancement-errata").size());
        assertEquals(0, errata.getJsonArray("new-subscription-security-errata").size());

        aggregateEventType(EVENT_TYPE_BUGFIX, 3);
        aggregateEventType(EVENT_TYPE_ENHANCEMENT, 2);
        aggregateEventType(EVENT_TYPE_SECURITY, 4);

        context = aggregator.getContext();
        errata = JsonObject.mapFrom(context).getJsonObject("errata");

        // each errata action have 3 events, we are checking total aggregated events
        assertEquals(3 * 3, errata.getJsonArray("new-subscription-bugfix-errata").size());
        assertEquals(2 * 3, errata.getJsonArray("new-subscription-enhancement-errata").size());
        assertEquals(4 * 3, errata.getJsonArray("new-subscription-security-errata").size());
    }

    void aggregateEventType(final String eventType, final int numberOfAggregations) {
        for (int i = 0; i < numberOfAggregations; i++) {
            aggregator.aggregate(TestHelpers.createEmailAggregationFromAction(ErrataTestHelpers.createErrataAction(eventType)));
        }
    }
}
