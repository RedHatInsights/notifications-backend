package com.redhat.cloud.notifications.connector.servicenow;

import io.quarkus.logging.Log;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;

import javax.enterprise.context.ApplicationScoped;

import static com.redhat.cloud.notifications.connector.servicenow.ExchangeProperty.KAFKA_PROCESSOR;

@ApplicationScoped
public class MigrationFilter implements Predicate {

    @Override
    public boolean matches(Exchange exchange) {
        String kafkaProcessor = exchange.getProperty(KAFKA_PROCESSOR, String.class);
        if ("connector".equals(kafkaProcessor)) {
            return true;
        } else {
            Log.info("Kafka message ignored because it is marked to be processed by the Eventing app");
            return false;
        }
    }
}
