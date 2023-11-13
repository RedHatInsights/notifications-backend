package com.redhat.cloud.notifications.connector;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.component.seda.SedaComponent;

@Dependent
public class SedaComponentConfigurator implements CamelComponentConfigurator {

    @Inject
    ConnectorConfig connectorConfig;

    @Override
    public void configure(CamelContext context) {
        SedaComponent component = context.getComponent("seda", SedaComponent.class);
        component.setConcurrentConsumers(connectorConfig.getSedaConcurrentConsumers());
        component.setQueueSize(connectorConfig.getSedaQueueSize());
        // The Kafka messages consumption is blocked (paused) when the SEDA queue is full.
        component.setDefaultBlockWhenFull(true);
        // The onException clauses will work with SEDA only if this is set to true.
        component.setBridgeErrorHandler(true);
        Log.debugf("Configured seda component");
    }
}
