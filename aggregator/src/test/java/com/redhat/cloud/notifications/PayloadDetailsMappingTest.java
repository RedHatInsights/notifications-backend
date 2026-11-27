package com.redhat.cloud.notifications;

import com.redhat.cloud.notifications.helpers.ResourceHelpers;
import com.redhat.cloud.notifications.helpers.TestHelpers;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.PayloadDetails;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.UUID;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The {@link PayloadDetails} entity lives in {@code common} and is shared with the engine. These tests prove the
 * aggregator's persistence unit maps it onto the existing {@code payload_details} table.
 */
@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
class PayloadDetailsMappingTest {

    @Inject
    ResourceHelpers helpers;

    @Inject
    EntityManager entityManager;

    @AfterEach
    void tearDown() {
        // Deleting the events cascades to the payload_details rows anchored to them.
        helpers.purgeEventAggregations();
    }

    @Test
    void unknownIdIsNotFound() {
        assertNull(entityManager.find(PayloadDetails.class, UUID.randomUUID()));
    }

    @Test
    void rowAnchoredToAnEventCanBeWrittenAndReadBack() {
        String payload = TestHelpers.generatePayloadContent("mappingOrg", "rhel", "policies", "policyId", "hostId").toString();
        Event event = helpers.addEventEmailAggregation("mappingOrg", "rhel", "policies", LocalDateTime.now(UTC).minusHours(1), payload, false);
        JsonObject contents = new JsonObject().put("org_id", "mappingOrg").put("payload", "value");
        PayloadDetails payloadDetails = new PayloadDetails(event, contents);

        QuarkusTransaction.requiringNew().run(() -> entityManager.persist(payloadDetails));

        assertNotNull(payloadDetails.getId());
        PayloadDetails found = entityManager.find(PayloadDetails.class, payloadDetails.getId());
        assertNotNull(found);
        assertEquals(event.getId(), found.getEventId());
        assertEquals(contents.encode(), found.getContents());
    }
}
