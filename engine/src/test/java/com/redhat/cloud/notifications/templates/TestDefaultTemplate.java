package com.redhat.cloud.notifications.templates;

import com.redhat.cloud.notifications.TestHelpers;
import com.redhat.cloud.notifications.ingress.Action;
import com.redhat.cloud.notifications.templates.models.Environment;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
public class TestDefaultTemplate {

    @Inject
    Environment environment;

    @Test
    public void testInstantEmailTitle() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        String result = Default.getTitle()
                .data("action", action)
                .render();

        assertTrue(result.contains("my-bundle/my-app/test-email-subscription-instant triggered"), "Title contains the bundle/app/event-type");
    }

    @Test
    public void testInstantEmailBody() {
        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");
        String result = Default.getBody(false, false)
                .data("action", action)
                .data("environment", environment)
                .render();

        assertTrue(result.contains("my-bundle/my-app/test-email-subscription-instant notification was triggered"), "Body should contain bundle/app/event-type");
        assertTrue(result.contains("You are receiving this email because the email template associated with this event type is not configured properly"));
    }

    @Test
    public void testWithTemplateCombinations() {
        final String titleFoundButNotBody = "The title template was found but not the body template.";
        final String bodyFoundButNotTitle = "The body template was found but not the title template.";

        Action action = TestHelpers.createPoliciesAction("", "my-bundle", "my-app", "FooMachine");

        // None
        String result = Default.getBody(false, false)
                .data("action", action)
                .data("environment", environment)
                .render();

        assertFalse(result.contains(titleFoundButNotBody));
        assertFalse(result.contains(bodyFoundButNotTitle));

        // Only title
        result = Default.getBody(true, false)
                .data("action", action)
                .data("environment", environment)
                .render();

        assertTrue(result.contains(titleFoundButNotBody));
        assertFalse(result.contains(bodyFoundButNotTitle));

        // Only body
        result = Default.getBody(false, true)
                .data("action", action)
                .data("environment", environment)
                .render();

        assertFalse(result.contains(titleFoundButNotBody));
        assertTrue(result.contains(bodyFoundButNotTitle));

        // both - but should never use this case
        result = Default.getBody(true, true)
                .data("action", action)
                .data("environment", environment)
                .render();

        assertFalse(result.contains(titleFoundButNotBody));
        assertFalse(result.contains(bodyFoundButNotTitle));
    }
}
