package com.redhat.cloud.notifications.connector;

import jakarta.enterprise.context.Dependent;
import org.apache.camel.CamelContext;

/**
 * Implement this interface in a {@link Dependent @Dependent} bean to configure a Camel component before the
 * Camel routes are initialized. Multiple implementations of this interface may coexist in the same application.
 */
public interface CamelComponentConfigurator {

    void configure(CamelContext context);
}
