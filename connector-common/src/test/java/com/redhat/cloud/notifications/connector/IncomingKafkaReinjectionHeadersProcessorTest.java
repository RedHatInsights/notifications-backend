package com.redhat.cloud.notifications.connector;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@QuarkusTest
public class IncomingKafkaReinjectionHeadersProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    IncomingKafkaReinjectionHeadersProcessor incomingKafkaReinjectionHeadersProcessor;

    /**
     * Test that the processor sets the reinjection count to zero if no
     * reinjection count was present in the message's Kafka header.
     */
    @Test
    void testProcessNoHeader() {
        final Exchange exchange = this.createExchangeWithBody("");

        // Call the processor under test.
        this.incomingKafkaReinjectionHeadersProcessor.process(exchange);

        Assertions.assertEquals(0, exchange.getProperty(ExchangeProperty.KAFKA_REINJECTION_COUNT), "when the header is not present, the reinjections count should be zero by default");
    }

    /**
     * Test that the processor properly reads the reinjection count from a
     * Kafka message's header if it is present.
     */
    @Test
    void testProcess() {
        final int reinjectionCount = 2;

        final Exchange exchange = this.createExchangeWithBody("");
        exchange.getMessage().setHeader(KafkaHeader.REINJECTION_COUNT, reinjectionCount);

        // Call the processor under test.
        this.incomingKafkaReinjectionHeadersProcessor.process(exchange);

        Assertions.assertEquals(reinjectionCount, exchange.getProperty(ExchangeProperty.KAFKA_REINJECTION_COUNT), "the reinjection count was not properly read from the Kafka header");
    }
}
