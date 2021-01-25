package com.redhat.cloud.notifications.models;

import com.redhat.cloud.notifications.models.Endpoint.EndpointType;
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

    @Test
    void endpointTypeDefaultIs2() {
        assertEquals(2, EndpointType.DEFAULT.ordinal());
    }
}
