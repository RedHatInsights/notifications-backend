package com.redhat.cloud.notifications.processors.camel;

import org.apache.camel.builder.endpoint.EndpointRouteBuilder;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ReturnRouteBuilder extends EndpointRouteBuilder {

    @ConfigProperty(name = "notifications.camel.kafka-return-topic")
    String kafkaReturnTopic;

    @Inject
    OutgoingCloudEventBuilder outgoingCloudEventBuilder;

    @Override
    public void configure() {
        from(direct("return"))
                .routeId("return")
                .process(outgoingCloudEventBuilder)
                .to(kafka(kafkaReturnTopic));
    }
}
