package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestEdgeManagementTemplate extends EmailTemplatesInDbHelper {

    static final String IMAGE_CREATION = "image-creation";
    static final String UPDATE_DEVICES = "update-devices";
    private static final Action ACTION = TestHelpers.createEdgeManagementAction();

    @Inject
    FeatureFlipper featureFlipper;

    @Inject
    EntityManager entityManager;

    @Override
    protected String getApp() {
        return "edge-management";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(IMAGE_CREATION, UPDATE_DEVICES);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(false);
        migrate();
    }

    @Test
    public void testImageCreationEmailTitle() {
        String result = generateEmailSubject(IMAGE_CREATION, ACTION);
        assertTrue(result.startsWith("Edge Management - Image Creation Started"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(IMAGE_CREATION, ACTION);
        assertEquals("Instant notification - Image creation started - Edge Management - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testImageCreationEmailBody() {
        String result = generateEmailBody(IMAGE_CREATION, ACTION);
        assertTrue(result.contains("A new image was created"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(IMAGE_CREATION, ACTION);
        assertTrue(result.contains("A new image named"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }

    @Test
    public void testUpdateDeviceEmailTitle() {
        String result = generateEmailSubject(UPDATE_DEVICES, ACTION);
        assertTrue(result.startsWith("Update Device Started"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailSubject(UPDATE_DEVICES, ACTION);
        assertEquals("Instant notification - Update device started - Edge Management - Red Hat Enterprise Linux", result);
    }

    @Test
    public void testUpdateDeviceEmailBody() {
        String result = generateEmailBody(UPDATE_DEVICES, ACTION);
        assertTrue(result.contains("An Update for the device"));

        entityManager.clear(); // The Hibernate L1 cache has to be cleared to remove V1 template that are still in there.

        featureFlipper.setEdgeManagementEmailTemplatesV2Enabled(true);
        migrate();
        result = generateEmailBody(UPDATE_DEVICES, ACTION);
        assertTrue(result.contains("An Update for the device"));
        assertTrue(result.contains(TestHelpers.HCC_LOGO_TARGET));
    }
}
