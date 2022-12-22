package com.redhat.cloud.notifications.db.repositories;

import com.redhat.cloud.notifications.TestLifecycleManager;
import com.redhat.cloud.notifications.db.ResourceHelpers;
import com.redhat.cloud.notifications.models.Bundle;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import javax.ws.rs.NotFoundException;

@QuarkusTest
@QuarkusTestResource(TestLifecycleManager.class)
public class BundleRepositoryTest {

    @Inject
    BundleRepository bundleRepository;

    @Inject
    ResourceHelpers resourceHelpers;

    /**
     * Tests that a bundle can be successfully found by its name.
     */
    @Test
    void testFindByName() {
        final String bundleName = "test-find-by-name-bundle";
        final Bundle bundle = this.resourceHelpers.createBundle(bundleName, "test-find-by-name-display-name-bundle");

        // Call the function under test.
        final Bundle result = this.bundleRepository.findByName(bundleName);

        Assertions.assertEquals(bundle.getId(), result.getId(), "unexpected bundle fetched by name from the database");
    }

    /**
     * Tests that a not found exception is thrown when the bundle cannot be found by its name.
     */
    @Test
    void testFindByNameBadRequest() {
        Assertions.assertThrows(NotFoundException.class, () -> this.bundleRepository.findByName("made-up-name"));
    }
}
