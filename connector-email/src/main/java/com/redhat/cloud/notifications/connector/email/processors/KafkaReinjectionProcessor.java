package com.redhat.cloud.notifications.connector.email.processors;

import io.quarkus.logging.Log;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import java.util.HashSet;
import java.util.Set;

import static com.redhat.cloud.notifications.connector.email.CloudEventHistoryBuilder.TOTAL_RECIPIENTS_KEY;
import static com.redhat.cloud.notifications.connector.email.constants.ExchangeProperty.RECIPIENTS_WITH_EMAIL_ERROR;

@ApplicationScoped
@Alternative
@Priority(0) // The value doesn't matter.
public class KafkaReinjectionProcessor extends com.redhat.cloud.notifications.connector.KafkaReinjectionProcessor {

    /**
     * Determines the reinjection delay from the reinjection count which should
     * have been extracted from the incoming message's reinjection header. If
     * for some reason we don't have that count, we default that count to zero.
     *
     * Also, it sets the original Cloud Event as the exchange's message body.
     * @param exchange the incoming exchange from the previous routes or error
     *                 handlers.
     */
    @Override
    public void process(final Exchange exchange) {
        super.process(exchange);

        final Message message = exchange.getMessage();

        message.setHeader(RECIPIENTS_WITH_EMAIL_ERROR, String.join(",", exchange.getProperty(RECIPIENTS_WITH_EMAIL_ERROR, new HashSet(), Set.class)));
        message.setHeader(TOTAL_RECIPIENTS_KEY, String.valueOf(exchange.getProperty(TOTAL_RECIPIENTS_KEY, Integer.class)));

        Log.info("on my KafkaReinjectionProcessor " + message.getHeader(TOTAL_RECIPIENTS_KEY, String.class) + " - " +  message.getHeader(RECIPIENTS_WITH_EMAIL_ERROR, String.class));

    }
}
