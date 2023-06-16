package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.EmailTemplatesInDbHelper;
import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.config.FeatureFlipper;
import com.redhat.cloud.notifications.ingress.Action;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestDefaultTemplate extends EmailTemplatesInDbHelper {

    private static final String EVENT_TYPE_NAME = "event-type-without-template";

    @Inject
    FeatureFlipper featureFlipper;

    @Override
    protected String getApp() {
        return "policies";
    }

    @Override
    protected List<String> getUsedEventTypeNames() {
        return List.of(EVENT_TYPE_NAME);
    }

    @BeforeEach
    void beforeEach() {
        featureFlipper.setUseDefaultTemplate(true);
    }

    @AfterEach
    void afterEach() {
        featureFlipper.setUseDefaultTemplate(false);
    }

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");

        String result = generateEmailSubject(EVENT_TYPE_NAME, action);
        assertTrue(result.contains("my-bundle/my-app/test-email-subscription-instant triggered"), "Title contains the bundle/app/event-type");
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        String result = generateEmailBody(EVENT_TYPE_NAME, action);
        assertTrue(result.contains("my-bundle/my-app/test-email-subscription-instant notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }
}
