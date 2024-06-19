package com.redhat.cloud.notifications.processors.email.connector.model;

import com.redhat.cloud.notifications.TestConstants;
import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.db.repositories.PayloadDetailsRepository;
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
        final PayloadDetails payloadDetails = new PayloadDetails(TestConstants.DEFAULT_ORG_ID, "test payload");

        // Store it in the database.
        this.payloadDetailsRepository.save(payloadDetails);
        this.entityManager.flush();

        // Fetch it and make sure that the data is correct.
        Assertions.assertEquals(1L, (Long) this.entityManager.createQuery("SELECT COUNT(*) FROM PayloadDetails").getSingleResult(), "a payload should have been stored in the database");

        final Optional<PayloadDetails> payloadDetailsOptional = this.payloadDetailsRepository.findById(payloadDetails.getId());

        if (payloadDetailsOptional.isEmpty()) {
            Assertions.fail("no results were returned when attempting to fetch a payload's details and a valid payload ID was specified");
        }

        final PayloadDetails fetchedPayloadDetails = payloadDetailsOptional.get();

        // Assert that the object got properly created.
        Assertions.assertEquals(payloadDetails.getId(), fetchedPayloadDetails.getId(), "the fetched payload ID is incorrect");
        Assertions.assertEquals(payloadDetails.getOrgId(), fetchedPayloadDetails.getOrgId(), "the organization ID is incorrect");
        Assertions.assertEquals(payloadDetails.getContents(), fetchedPayloadDetails.getContents(), "the fetched payload is incorrect");

        // Delete the payload from the database.
        this.payloadDetailsRepository.deleteById(fetchedPayloadDetails.getId());
        this.entityManager.clear();

        // Assert that the object got properly deleted.
        Assertions.assertEquals(0L, (Long) this.entityManager.createQuery("SELECT COUNT(*) FROM PayloadDetails").getSingleResult(), "the database should not contain any payloads left after using the delete function from the repository");

        final PayloadDetails objectAfterDelete = this.entityManager.find(PayloadDetails.class, fetchedPayloadDetails.getId());

        Assertions.assertNull(objectAfterDelete);
    }
}
