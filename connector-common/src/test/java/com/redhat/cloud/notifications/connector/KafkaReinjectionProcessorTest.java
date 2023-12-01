package com.redhat.cloud.notifications.connector;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.apache.camel.Exchange;
import org.apache.camel.quarkus.test.CamelQuarkusTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.redhat.cloud.notifications.connector.ExchangeProperty.KAFKA_REINJECTION_COUNT;
import static com.redhat.cloud.notifications.connector.ExchangeProperty.KAFKA_REINJECTION_DELAY;
import static com.redhat.cloud.notifications.connector.IncomingCloudEventFilter.X_RH_NOTIFICATIONS_CONNECTOR_HEADER;

@QuarkusTest
public class KafkaReinjectionProcessorTest extends CamelQuarkusTestSupport {
    @Inject
    ConnectorConfig connectorConfig;

    @Inject
    KafkaReinjectionProcessor kafkaReinjectionProcessor;

    /**
     * Tests that the expected body and headers are set for the exchange
     * message once we are in the message reinjection process.
     */
    @Test
    void processReinjections() {
        // Prepare a small set of test cases to make sure the logic is correct.
        record TestCase(Integer reinjectedCount, long expectedDelay) { }

        final List<TestCase> testCases = List.of(
            new TestCase(null, TimeUnit.SECONDS.toMillis(10)),
            new TestCase(0, TimeUnit.SECONDS.toMillis(10)),
            new TestCase(1, TimeUnit.SECONDS.toMillis(30)),
            new TestCase(2, TimeUnit.MINUTES.toMillis(1)),
            new TestCase(5, this.connectorConfig.getIncomingKafkaMaxPollIntervalMs() / 2)
        );

        for (final TestCase t : testCases) {
            // Prepare the exchange message.
            final String originalCloudEvent = "original cloud event body";
            final Exchange exchange = this.createExchangeWithBody("");
            exchange.setProperty(ExchangeProperty.ORIGINAL_CLOUD_EVENT, originalCloudEvent);
            exchange.setProperty(KAFKA_REINJECTION_COUNT, t.reinjectedCount());

            // Call the processor under test.
            this.kafkaReinjectionProcessor.process(exchange);

            // Assert that the exchange contains the correct values.
            Assertions.assertEquals(t.expectedDelay(), exchange.getProperty(KAFKA_REINJECTION_DELAY), String.format("the Kafka reinjection delay is incorrect for reinjection count value '%d'", 0));
            Assertions.assertEquals(originalCloudEvent, exchange.getMessage().getBody(), "the body should be set to the original Cloud Event so that it gets published to Kafka");

            final Map<String, Object> headers = exchange.getMessage().getHeaders();
            Assertions.assertEquals(this.connectorConfig.getConnectorName(), headers.get(X_RH_NOTIFICATIONS_CONNECTOR_HEADER), "the connector's name should be set in the header so that when the message gets reinjected it doesn't get filtered by the incoming cloud event filter");
            Assertions.assertEquals(Objects.requireNonNullElse(t.reinjectedCount(), 0) + 1, Integer.parseInt((String) headers.get(KafkaHeader.REINJECTION_COUNT)), "the reinjection was improperly incremented");
        }
    }
}
