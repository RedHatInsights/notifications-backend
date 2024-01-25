package com.redhat.cloud.notifications.connector;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.KAFKA_REINJECTION_COUNT;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.KAFKA_REINJECTION_DELAY;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;

@ApplicationScoped
@DefaultBean
public class KafkaReinjectionProcessor implements Processor {
    @Inject
    ConnectorConfig connectorConfig;

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
        final int reinjectionCount = exchange.getProperty(KAFKA_REINJECTION_COUNT, 0, int.class);

        final long reinjectionDelay = switch (reinjectionCount) {
            case 0 -> connectorConfig.getReinjectionDelayBeforeFistAttempt().toMillis();
            case 1 -> connectorConfig.getReinjectionDelayBeforeSecondAttempt().toMillis();
            case 2 -> connectorConfig.getReinjectionDelayBeforeThirdAttempt().toMillis();
            default -> this.connectorConfig.getIncomingKafkaMaxPollIntervalMs() / 2;
        };

        exchange.setProperty(KAFKA_REINJECTION_DELAY, reinjectionDelay);

        final Message message = exchange.getMessage();
        message.setBody(exchange.getProperty(ExchangeProperty.ORIGINAL_CLOUD_EVENT));

        message.setHeader(X_RH_NOTIFICATIONS_CONNECTOR_HEADER, this.connectorConfig.getConnectorName());
        // The value of the header needs to be converted to string, since
        // otherwise it does not get properly pushed to Kafka. When attempted
        // to send the integer directly, the value simply was not there.
        message.setHeader(KafkaHeader.REINJECTION_COUNT, String.valueOf(reinjectionCount + 1));
    }
}
