package com.redhat.cloud.notifications.connector.v2;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Test implementation of MessageHandler for unit testing
 * Used as fallback when no specific MessageHandler implementation is available
 */
@ApplicationScoped
public class TestMessageHandler implements MessageHandler {

    @Override
    public void handle(MessageContext context) throws Exception {
        Log.info("Test implementation of MessageHandler for unit testing");
    }
}
