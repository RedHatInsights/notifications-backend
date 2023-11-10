package com.redhat.cloud.notifications.processors.email;

import com.redhat.cloud.notifications.models.Event;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class EmailActorsResolverTest {
    @Inject
    EmailActorsResolver emailActorsResolver;

    /**
     * Tests that the default "RH Insights" sender is returned for every event.
     */
    @Test
    void testGetEmailSender() {
        Assertions.assertEquals(EmailActorsResolver.RH_INSIGHTS_SENDER, this.emailActorsResolver.getEmailSender(new Event()), "unexpected email sender returned from the function under test");
    }
}
