package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Application;
import com.redhat.cloud.notifications.models.Bundle;
import com.redhat.cloud.notifications.models.InternalRoleAccess;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@QuarkusTest
public class InternalRoleAccessRepositoryTest {
    @Inject
    InternalRoleAccessRepository internalRoleAccessRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that the function under test is able to find the associated
     * internal role access object of the application.
     */
    @Test
    void testFindOneByApplicationUUID() {
        // Prepare the fixtures.
        final Bundle bundle = this.resourceHelpers.createBundle("test-find-one-application-uuid");
        final Application application = this.resourceHelpers.createApplication(bundle.getId());

        final InternalRoleAccess internalRoleAccess = new InternalRoleAccess();
        internalRoleAccess.setApplication(application);
        internalRoleAccess.setApplicationId(application.getId());
        internalRoleAccess.setRole("test-internal-role-access");

        this.internalRoleAccessRepository.addAccess(internalRoleAccess);

        // Call the function under test.
        final InternalRoleAccess result = this.internalRoleAccessRepository.findOneByApplicationUUID(application.getId());

        // Assert that the correct internal role was found in the database.
        Assertions.assertEquals(internalRoleAccess.getId(), result.getId(), "the function under test found an internal access role with an unexpected ID");
        Assertions.assertEquals(internalRoleAccess.getApplication(), result.getApplication(), "the function under test found an internal access role with an unexpected application");
        Assertions.assertEquals(internalRoleAccess.getApplicationId(), result.getApplicationId(), "the function under test found an internal access role with an unexpected application ID");
        Assertions.assertEquals(internalRoleAccess.getInternalRole(), result.getInternalRole(), "the function under test found an internal access role with an unexpected internal role");
    }

    /**
     * Tests that when there is no internal role associated to an application,
     * then the function under test returns null.
     */
    @Test
    void testFindOneByApplicationUUIDNull() {
        // Call the function under test.
        final InternalRoleAccess result = this.internalRoleAccessRepository.findOneByApplicationUUID(UUID.randomUUID());

        // Assert that no role was found in the database.
        Assertions.assertNull(result, "the function under test found an internal access role when none should have been found");
    }
}
