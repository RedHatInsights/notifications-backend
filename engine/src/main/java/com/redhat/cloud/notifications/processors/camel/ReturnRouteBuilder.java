package com.redhat.cloud.notifications.processors.camel;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ReturnRouteBuilder extends EndpointRouteBuilder {

    public static final String RETURN_ROUTE_NAME = "return";

    @ConfigProperty(name = "mp.messaging.fromcamel.topic")
    String kafkaReturnTopic;

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Override
    public void configure() {
        from(direct(RETURN_ROUTE_NAME))
                .routeId(RETURN_ROUTE_NAME)
                .process(outgoingCloudEventBuilder)
                .to(kafka(kafkaReturnTopic));
    }
}
