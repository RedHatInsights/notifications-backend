package com.redhat.cloud.notifications.connector.email.processors.rbac.group;

import com.redhat.cloud.notifications.connector.email.processors.rbac.RBACConstants;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class RBACGroupProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    RBACGroupProcessor rbacGroupProcessor;

    /**
     * Tests that the "platform default" property is correctly extracted from
     * the incoming payload, and that it is properly set in an exchange
     * property.
     */
    @Test
    void testProcess() {
        // Prepare the expected incoming body for the test.
        final boolean isPlatformDefault = true;
        final JsonObject incomingResponse = new JsonObject();
        incomingResponse.put("platform_default", isPlatformDefault);

        // Create the exchange simulating the JSON response we would get from
        // RBAC.
        final Exchange exchange = this.createExchangeWithBody(incomingResponse.encode());

        // Process the exchange with the processor under test.
        this.rbacGroupProcessor.process(exchange);

        // Assert that the property got correctly extracted.
        Assertions.assertEquals(isPlatformDefault, exchange.getProperty(RBACConstants.RBAC_GROUP_IS_PLATFORM_DEFAULT, Boolean.class), "the \"platform_default\" property did not contain the expected value");

    }
}
