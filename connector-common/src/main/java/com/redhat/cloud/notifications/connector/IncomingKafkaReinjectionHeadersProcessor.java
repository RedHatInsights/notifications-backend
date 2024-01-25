package com.redhat.cloud.notifications.connector;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

@ApplicationScoped
public class IncomingKafkaReinjectionHeadersProcessor implements Processor {
    /**
     * Extracts the reinjection count from the Kafka header if it is present.
     * It defaults the count to zero otherwise.
     * @param exchange the exchange representing the Kafka message.
     */
    @Override
    public void process(final Exchange exchange) {
        final int reinjectionCount = exchange.getIn().getHeader(KafkaHeader.REINJECTION_COUNT, 0, int.class);

        exchange.setProperty(ExchangeProperty.KAFKA_REINJECTION_COUNT, reinjectionCount);
    }
}
