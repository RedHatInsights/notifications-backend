package com.redhat.cloud.notifications.processors.email.connector.model;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.Event;
import com.redhat.cloud.notifications.models.EventType;
import com.redhat.cloud.notifications.processors.payload.PayloadDetails;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class PayloadDetailsRepositoryTest {
    @Inject
    EntityManager entityManager;

    @Inject
    PayloadDetailsRepository payloadDetailsRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that the email details can be properly saved in the database,
     * fetched from it and then deleted.
     */
    @Test
    @Transactional
    void testSaveFetchDeleteEmailDetails() {
        // Create the test object.
        final Bundle bundle = this.resourceHelpers.createBundle("test-save-email-details-bundle");
        final Application application = this.resourceHelpers.createApp(bundle.getId(), "test-save-email-details-app");
        final EventType eventType = this.resourceHelpers.createEventType(application.getId(), "test-save-email-details-event-type");
        final Event event = this.resourceHelpers.createEvent(eventType);

        final String payload = "test payload";

        final PayloadDetails payloadDetails = new PayloadDetails(event, payload);

        // Store it in the database.
        this.payloadDetailsRepository.save(payloadDetails);
        this.entityManager.flush();

        // Fetch it and make sure that the data is correct.
        Assertions.assertEquals(1L, (Long) this.entityManager.createQuery("SELECT COUNT(*) FROM PayloadDetails").getSingleResult(), "a payload should have been stored in the database");

        final Optional<PayloadDetails> payloadDetailsOptional = this.payloadDetailsRepository.findByEventId(event.getId());

        if (payloadDetailsOptional.isEmpty()) {
            Assertions.fail("no results were returned when attempting to fetch a payload's details and a valid event ID was specified");
        }

        final PayloadDetails fetchedPayloadDetails = payloadDetailsOptional.get();

        // Assert that the object got properly created.
        Assertions.assertEquals(event.getId(), fetchedPayloadDetails.getEventId(), "the fetched event ID is incorrect");
        Assertions.assertEquals(event.getOrgId(), fetchedPayloadDetails.getOrgId(), "the organization ID is incorrect");
        Assertions.assertEquals(payload, fetchedPayloadDetails.getContents(), "the fetched payload is incorrect");

        // Delete the payload from the database.
        this.payloadDetailsRepository.deleteById(fetchedPayloadDetails.getEventId());
        this.entityManager.clear();

        // Assert that the object got properly deleted.
        Assertions.assertEquals(0L, (Long) this.entityManager.createQuery("SELECT COUNT(*) FROM PayloadDetails").getSingleResult(), "the database should not contain any payloads left after using the delete function from the repository");

        final PayloadDetails objectAfterDelete = this.entityManager.find(PayloadDetails.class, fetchedPayloadDetails.getEventId());

        Assertions.assertNull(objectAfterDelete);
    }
}
