package com.redhat.cloud.notifications.models;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class TestEndpointType {

    @Test
    void endpointTypeWebhookIs0() {
        assertEquals(0, EndpointType.WEBHOOK.ordinal());
    }

    @Test
    void endpointTypeEmailSubscriptionIs1() {
        assertEquals(1, EndpointType.EMAIL_SUBSCRIPTION.ordinal());
    }

}
